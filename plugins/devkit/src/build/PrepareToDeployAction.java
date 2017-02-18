/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.make.ManifestBuilder;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: anna
 * Date: May 5, 2005
 */
public class PrepareToDeployAction extends AnAction {
  @NonNls private static final String ZIP_EXTENSION = ".zip";
  @NonNls private static final String JAR_EXTENSION = ".jar";
  @NonNls private static final String TEMP_PREFIX = "temp";
  @NonNls private static final String MIDDLE_LIB_DIR = "lib";

  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup("Plugin DevKit Deployment");

  public void actionPerformed(final AnActionEvent e) {
    final Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (module != null && PluginModuleType.isOfType(module)) {
      doPrepare(Arrays.asList(module), e.getProject());
    }
  }

  public void doPrepare(final List<Module> pluginModules, final Project project) {
    final List<String> errorMessages = new ArrayList<>();
    final List<String> successMessages = new ArrayList<>();

    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.make(compilerManager.createModulesCompileScope(pluginModules.toArray(new Module[pluginModules.size()]), true),
                         new CompileStatusNotification() {
                           public void finished(final boolean aborted,
                                                final int errors,
                                                final int warnings,
                                                final CompileContext compileContext) {
                             if (aborted || errors != 0) return;
                             ApplicationManager.getApplication().invokeLater(() -> {
                               for (Module aModule : pluginModules) {
                                 if (!doPrepare(aModule, errorMessages, successMessages)) {
                                   return;
                                 }
                               }

                               if (!errorMessages.isEmpty()) {
                                 Messages.showErrorDialog(errorMessages.iterator().next(), DevKitBundle.message("error.occurred"));
                               }
                               else if (!successMessages.isEmpty()) {
                                 StringBuilder messageBuf = new StringBuilder();
                                 for (String message : successMessages) {
                                   if (messageBuf.length() != 0) {
                                     messageBuf.append('\n');
                                   }
                                   messageBuf.append(message);
                                 }
                                 final String title = pluginModules.size() == 1 ?
                                                      DevKitBundle.message("success.deployment.message", pluginModules.get(0).getName()) :
                                                      DevKitBundle.message("success.deployment.message.all");
                                 NOTIFICATION_GROUP.createNotification(title, messageBuf.toString(),
                                                                       NotificationType.INFORMATION, null).notify(project);
                               }
                             }, project.getDisposed());
                           }
                         });
  }

  public static boolean doPrepare(final Module module, final List<String> errorMessages, final List<String> successMessages) {
    final String pluginName = module.getName();
    final String defaultPath = new File(module.getModuleFilePath()).getParent() + File.separator + pluginName;
    final HashSet<Module> modules = new HashSet<>();
    PluginBuildUtil.getDependencies(module, modules);
    modules.add(module);
    final Set<Library> libs = new HashSet<>();
    for (Module dep : modules) {
      PluginBuildUtil.getLibraries(dep, libs);
    }

    final Map<Module, String> jpsModules = collectJpsPluginModules(module);
    modules.removeAll(jpsModules.keySet());

    final boolean isZip = !libs.isEmpty() || !jpsModules.isEmpty();
    final String oldPath = defaultPath + (isZip ? JAR_EXTENSION : ZIP_EXTENSION);
    final File oldFile = new File(oldPath);
    if (oldFile.exists()) {
      if (Messages
        .showYesNoDialog(module.getProject(), DevKitBundle.message("suggest.to.delete", oldPath), DevKitBundle.message("info.message"),
                         Messages.getInformationIcon()) == Messages.YES) {
        FileUtil.delete(oldFile);
      }
    }

    final String dstPath = defaultPath + (isZip ? ZIP_EXTENSION : JAR_EXTENSION);
    final File dstFile = new File(dstPath);
    return clearReadOnly(module.getProject(), dstFile) && ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {

      final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null) {
        progressIndicator.setText(DevKitBundle.message("prepare.for.deployment.common"));
        progressIndicator.setIndeterminate(true);
      }
      try {
        File jarFile = preparePluginsJar(module, modules);
        if (isZip) {
          processLibrariesAndJpsPlugins(jarFile, dstFile, pluginName, libs, jpsModules, progressIndicator);
        }
        else {
          FileUtil.copy(jarFile, dstFile);
        }
        LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(dstFile), true, false, null);
        successMessages.add(DevKitBundle.message("saved.message", isZip ? 1 : 2, pluginName, dstPath));
      }
      catch (final IOException e) {
        errorMessages.add(e.getMessage() + "\n(" + dstPath + ")");
      }
    }, DevKitBundle.message("prepare.for.deployment", pluginName), true, module.getProject());
  }

  @NotNull
  private static Map<Module, String> collectJpsPluginModules(@NotNull Module module) {
    XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml == null) return Collections.emptyMap();

    DomFileElement<IdeaPlugin> fileElement = DomManager.getDomManager(module.getProject()).getFileElement(pluginXml, IdeaPlugin.class);
    if (fileElement == null) return Collections.emptyMap();

    Map<Module, String> jpsPluginToOutputPath = new HashMap<>();
    IdeaPlugin plugin = fileElement.getRootElement();
    List<Extensions> extensions = plugin.getExtensions();
    for (Extensions extensionGroup : extensions) {
      XmlTag extensionsTag = extensionGroup.getXmlTag();
      String defaultExtensionNs = extensionsTag.getAttributeValue("defaultExtensionNs");
      for (XmlTag tag : extensionsTag.getSubTags()) {
        String name = tag.getLocalName();
        String qualifiedName = defaultExtensionNs != null ? defaultExtensionNs + "." + name : name;
        if (CompileServerPlugin.EP_NAME.getName().equals(qualifiedName)) {
          String classpath = tag.getAttributeValue("classpath");
          if (classpath != null) {
            for (String path : StringUtil.split(classpath, ";")) {
              String moduleName = FileUtil.getNameWithoutExtension(PathUtil.getFileName(path));
              Module jpsModule = ModuleManager.getInstance(module.getProject()).findModuleByName(moduleName);
              if (jpsModule != null) {
                jpsPluginToOutputPath.put(jpsModule, path);
              }
            }
          }
        }
      }
    }
    return jpsPluginToOutputPath;
  }

  private static boolean clearReadOnly(final Project project, final File dstFile) {
    //noinspection EmptyCatchBlock
    final URL url;
    try {
      url = dstFile.toURL();
    }
    catch (MalformedURLException e) {
      return true;
    }
    final VirtualFile vfile = VfsUtil.findFileByURL(url);
    return vfile == null || !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(vfile).hasReadonlyFiles();
  }

  private static FileFilter createFilter(final ProgressIndicator progressIndicator, @Nullable final FileTypeManager fileTypeManager) {
    return pathName -> {
      if (progressIndicator != null) {
        progressIndicator.setText2("");
      }
      return fileTypeManager == null || !fileTypeManager.isFileIgnored(FileUtil.toSystemIndependentName(pathName.getName()));
    };
  }

  private static void processLibrariesAndJpsPlugins(final File jarFile, final File zipFile, final String pluginName,
                                                    final Set<Library> libs,
                                                    Map<Module, String> jpsModules, final ProgressIndicator progressIndicator) throws IOException {
    if (FileUtil.ensureCanCreateFile(zipFile)) {
      ZipOutputStream zos = null;
      try {
        zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
        addStructure(pluginName, zos);
        addStructure(pluginName + "/" + MIDDLE_LIB_DIR, zos);
        final String entryName = pluginName + JAR_EXTENSION;
        ZipUtil.addFileToZip(zos, jarFile, getZipPath(pluginName, entryName), new HashSet<>(),
                             createFilter(progressIndicator, FileTypeManager.getInstance()));
        for (Map.Entry<Module, String> entry : jpsModules.entrySet()) {
          File jpsPluginJar = jarModulesOutput(Collections.singleton(entry.getKey()), null, null);
          ZipUtil.addFileToZip(zos, jpsPluginJar, getZipPath(pluginName, entry.getValue()), null, null);
        }
        Set<String> usedJarNames = new HashSet<>();
        usedJarNames.add(entryName);
        Set<VirtualFile> jarredVirtualFiles = new HashSet<>();
        for (Library library : libs) {
          final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          for (VirtualFile virtualFile : files) {
            if (jarredVirtualFiles.add(virtualFile)) {
              if (virtualFile.getFileSystem() instanceof JarFileSystem) {
                addLibraryJar(virtualFile, zipFile, pluginName, zos, usedJarNames, progressIndicator);
              }
              else {
                makeAndAddLibraryJar(virtualFile, zipFile, pluginName, zos, usedJarNames, progressIndicator, library.getName());
              }
            }
          }
        }
      }
      finally {
        if (zos != null) zos.close();
      }
    }
  }

  private static String getZipPath(final String pluginName, final String entryName) {
    return "/" + pluginName + "/" + MIDDLE_LIB_DIR + "/" + entryName;
  }

  private static void makeAndAddLibraryJar(final VirtualFile virtualFile,
                                           final File zipFile,
                                           final String pluginName,
                                           final ZipOutputStream zos,
                                           final Set<String> usedJarNames,
                                           final ProgressIndicator progressIndicator,
                                           final String preferredName) throws IOException {
    File libraryJar = FileUtil.createTempFile(TEMP_PREFIX, JAR_EXTENSION);
    libraryJar.deleteOnExit();
    ZipOutputStream jar = null;
    try {
      jar = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(libraryJar)));
      ZipUtil.addFileOrDirRecursively(jar, libraryJar, VfsUtilCore.virtualToIoFile(virtualFile), "",
                                      createFilter(progressIndicator, FileTypeManager.getInstance()), null);
    }
    finally {
      if (jar != null) jar.close();
    }
    final String jarName =
      getLibraryJarName(virtualFile.getName() + JAR_EXTENSION, usedJarNames, preferredName == null ? null : preferredName + JAR_EXTENSION);
    ZipUtil.addFileOrDirRecursively(zos, zipFile, libraryJar, getZipPath(pluginName, jarName), createFilter(progressIndicator, null), null);
  }

  private static String getLibraryJarName(final String fileName, Set<String> usedJarNames, @Nullable final String preferredName) {
    String uniqueName;
    if (preferredName != null && !usedJarNames.contains(preferredName)) {
      uniqueName = preferredName;
    }
    else {
      uniqueName = fileName;
      if (usedJarNames.contains(uniqueName)) {
        final int dotPos = uniqueName.lastIndexOf(".");
        final String name = dotPos < 0 ? uniqueName : uniqueName.substring(0, dotPos);
        final String ext = dotPos < 0 ? "" : uniqueName.substring(dotPos);

        int i = 0;
        do {
          i++;
          uniqueName = name + i + ext;
        }
        while (usedJarNames.contains(uniqueName));
      }
    }
    usedJarNames.add(uniqueName);
    return uniqueName;
  }

  private static void addLibraryJar(final VirtualFile virtualFile,
                                    final File zipFile,
                                    final String pluginName,
                                    final ZipOutputStream zos,
                                    final Set<String> usedJarNames,
                                    final ProgressIndicator progressIndicator) throws IOException {
    File ioFile = VfsUtil.virtualToIoFile(virtualFile);
    final String jarName = getLibraryJarName(ioFile.getName(), usedJarNames, null);
    ZipUtil.addFileOrDirRecursively(zos, zipFile, ioFile, getZipPath(pluginName, jarName), createFilter(progressIndicator, null), null);
  }

  private static void addStructure(@NonNls final String relativePath, final ZipOutputStream zos) throws IOException {
    ZipEntry e = new ZipEntry(relativePath + "/");
    e.setMethod(ZipEntry.STORED);
    e.setSize(0);
    e.setCrc(0);
    zos.putNextEntry(e);
    zos.closeEntry();
  }

  private static File preparePluginsJar(Module module, final HashSet<Module> modules) throws IOException {
    final PluginBuildConfiguration pluginModuleBuildProperties = PluginBuildConfiguration.getInstance(module);
    final Manifest manifest = createOrFindManifest(pluginModuleBuildProperties);

    return jarModulesOutput(modules, manifest, pluginModuleBuildProperties.getPluginXmlPath());
  }

  private static File jarModulesOutput(@NotNull Set<Module> modules, @Nullable Manifest manifest, final @Nullable String pluginXmlPath) throws IOException {
    File jarFile = FileUtil.createTempFile(TEMP_PREFIX, JAR_EXTENSION);
    jarFile.deleteOnExit();
    ZipOutputStream jarPlugin = null;
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(jarFile));
      jarPlugin = manifest != null ? new JarOutputStream(out, manifest) : new JarOutputStream(out);
      final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      final Set<String> writtenItemRelativePaths = new HashSet<>();
      for (Module module : modules) {
        final VirtualFile compilerOutputPath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
        if (compilerOutputPath == null) continue; //pre-condition: output dirs for all modules are up-to-date
        ZipUtil.addDirToZipRecursively(jarPlugin, jarFile, new File(compilerOutputPath.getPath()), "",
                                       createFilter(progressIndicator, FileTypeManager.getInstance()), writtenItemRelativePaths);
      }
      if (pluginXmlPath != null) {
        ZipUtil.addFileToZip(jarPlugin, new File(pluginXmlPath), "/META-INF/plugin.xml", writtenItemRelativePaths,
                             createFilter(progressIndicator, null));
      }
    }
    finally {
      if (jarPlugin != null) jarPlugin.close();
    }
    return jarFile;
  }

  public static Manifest createOrFindManifest(final PluginBuildConfiguration pluginModuleBuildProperties) throws IOException {
    final Manifest manifest = new Manifest();
    final VirtualFile vManifest = pluginModuleBuildProperties.getManifest();
    if (pluginModuleBuildProperties.isUseUserManifest() && vManifest != null) {
      InputStream in = null;
      try {
        in = new BufferedInputStream(vManifest.getInputStream());
        manifest.read(in);
      }
      finally {
        if (in != null) in.close();
      }
    }
    else {
      Attributes mainAttributes = manifest.getMainAttributes();
      ManifestBuilder.setGlobalAttributes(mainAttributes);
    }
    return manifest;
  }

  public void update(AnActionEvent e) {
    final Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    boolean enabled = module != null && PluginModuleType.isOfType(module);
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
    if (enabled) {
      e.getPresentation().setText(DevKitBundle.message("prepare.for.deployment", module.getName()));
    }
  }
}
