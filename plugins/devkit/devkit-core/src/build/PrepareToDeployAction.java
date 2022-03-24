// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build;

import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
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
import com.intellij.openapi.roots.NativeLibraryOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extensions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginDescriptorConstants;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.Manifest;

public class PrepareToDeployAction extends AnAction {
  private static final @NonNls String ZIP_EXTENSION = ".zip";
  private static final @NonNls String JAR_EXTENSION = ".jar";
  private static final @NonNls String TEMP_PREFIX = "temp";

  private static class Holder {
    private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Plugin DevKit Deployment");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    if (module != null && PluginModuleType.isOfType(module)) {
      doPrepare(Collections.singletonList(module), e.getProject());
    }
  }

  public void doPrepare(List<Module> pluginModules, Project project) {
    List<String> errorMessages = new ArrayList<>();
    List<String> successMessages = new ArrayList<>();
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createModulesCompileScope(pluginModules.toArray(Module.EMPTY_ARRAY), true);
    compilerManager.make(scope, new CompileStatusNotification() {
      @Override
      public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
        if (aborted || errors != 0) return;
        ApplicationManager.getApplication().invokeLater(() -> {
          for (Module module : pluginModules) {
            if (!doPrepare(module, errorMessages, successMessages)) {
              return;
            }
          }
          if (!errorMessages.isEmpty()) {
            @NlsSafe String errorMessage = errorMessages.iterator().next();
            Messages.showErrorDialog(errorMessage, DevKitBundle.message("error.occurred"));
          }
          else if (!successMessages.isEmpty()) {
            String title = pluginModules.size() == 1 ?
                           DevKitBundle.message("success.deployment.message", pluginModules.get(0).getName()) :
                           DevKitBundle.message("success.deployment.message.all");
            @NlsSafe String successMessage = StringUtil.join(successMessages, "\n");
            Holder.NOTIFICATION_GROUP.createNotification(title, successMessage, NotificationType.INFORMATION).notify(project);
          }
        }, project.getDisposed());
      }
    });
  }

  public static boolean doPrepare(Module module, List<String> errorMessages, List<String> successMessages) {
    String pluginName = module.getName();
    String defaultPath = new File(module.getModuleFilePath()).getParent() + File.separator + pluginName;
    Set<Module> modules = new HashSet<>();
    PluginBuildUtil.getDependencies(module, modules);
    modules.add(module);
    Set<Library> libs = new HashSet<>();
    for (Module dep : modules) {
      PluginBuildUtil.getLibraries(dep, libs);
    }

    Map<Module, String> jpsModules = collectJpsPluginModules(module);
    modules.removeAll(jpsModules.keySet());

    boolean isZip = !libs.isEmpty() || !jpsModules.isEmpty();
    String oldPath = defaultPath + (isZip ? JAR_EXTENSION : ZIP_EXTENSION);
    File oldFile = new File(oldPath);
    if (oldFile.exists()) {
      String message = DevKitBundle.message("suggest.to.delete", oldPath), title = DevKitBundle.message("info.message");
      if (Messages.showYesNoDialog(module.getProject(), message, title, Messages.getInformationIcon()) == Messages.YES) {
        FileUtil.delete(oldFile);
      }
    }

    String dstPath = defaultPath + (isZip ? ZIP_EXTENSION : JAR_EXTENSION);
    File dstFile = new File(dstPath);
    return clearReadOnly(module.getProject(), dstFile) && ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null) {
        progressIndicator.setText(DevKitBundle.message("prepare.for.deployment.task.progress"));
        progressIndicator.setIndeterminate(true);
      }
      try {
        File jarFile = preparePluginsJar(module, modules);
        if (isZip) {
          try {
            processLibrariesAndJpsPlugins(jarFile, dstFile, pluginName, libs, jpsModules);
          }
          finally {
            FileUtil.delete(jarFile);
          }
        }
        else {
          FileUtil.rename(jarFile, dstFile);
        }
        LocalFileSystem.getInstance().refreshIoFiles(Collections.singleton(dstFile), true, false, null);
        successMessages.add(DevKitBundle.message("saved.message", isZip ? 1 : 2, pluginName, dstPath));
      }
      catch (IOException e) {
        errorMessages.add(e.getMessage() + "\n(" + dstPath + ")");
      }
    }, DevKitBundle.message("prepare.for.deployment.task", pluginName), true, module.getProject());
  }

  @NotNull
  private static Map<Module, String> collectJpsPluginModules(@NotNull Module module) {
    XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml == null) return Collections.emptyMap();

    IdeaPlugin plugin = DescriptorUtil.getIdeaPlugin(pluginXml);
    if (plugin == null) return Collections.emptyMap();

    Map<Module, String> jpsPluginToOutputPath = new HashMap<>();
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
              String moduleName = FileUtilRt.getNameWithoutExtension(PathUtil.getFileName(path));
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

  private static boolean clearReadOnly(Project project, File dstFile) {
    VirtualFile vfile = VfsUtil.findFileByIoFile(dstFile, true);
    return vfile == null || !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Collections.singleton(vfile)).hasReadonlyFiles();
  }

  private static void processLibrariesAndJpsPlugins(File jarFile,
                                                    File zipFile,
                                                    String pluginName,
                                                    Set<Library> libs,
                                                    Map<Module, String> jpsModules) throws IOException {
    if (FileUtil.ensureCanCreateFile(zipFile)) {
      try (Compressor zip = new Compressor.Zip(zipFile)) {
        zip.addDirectory(getZipPath(pluginName, ""));

        Set<String> usedFileNames = new HashSet<>();
        String entryName = pluginName + JAR_EXTENSION;
        zip.addFile(getZipPath(pluginName, entryName), jarFile);
        usedFileNames.add(entryName);

        for (Map.Entry<Module, String> entry : jpsModules.entrySet()) {
          File jpsPluginJar = jarModulesOutput(Collections.singleton(entry.getKey()), null, null);
          try {
            zip.addFile(getZipPath(pluginName, entry.getValue()), jpsPluginJar);
          }
          finally {
            FileUtil.delete(jpsPluginJar);
          }
        }

        Set<VirtualFile> jarredVirtualFiles = new HashSet<>();
        for (Library library : libs) {
          VirtualFile[] roots = library.getFiles(OrderRootType.CLASSES);
          for (VirtualFile libRoot : roots) {
            if (jarredVirtualFiles.add(libRoot)) {
              if (libRoot.getFileSystem() instanceof JarFileSystem) {
                addLibraryFile(libRoot, pluginName, zip, usedFileNames);
              }
              else {
                makeAndAddLibraryJar(libRoot, pluginName, zip, usedFileNames, library.getName());
              }
            }
          }
          VirtualFile[] nativeRoots = library.getFiles(NativeLibraryOrderRootType.getInstance());
          for (VirtualFile root : nativeRoots) {
            if (!root.isDirectory()) {
              addLibraryFile(root, pluginName, zip, usedFileNames);
            }
          }
        }
      }
    }
  }

  private static @NonNls String getZipPath(String pluginName, String entryName) {
    return pluginName + "/lib/" + entryName;
  }

  private static void addLibraryFile(VirtualFile root, String pluginName, Compressor zip, Set<String> usedFileNames) throws IOException {
    File ioFile = VfsUtilCore.virtualToIoFile(root);
    String fileName = getLibraryFileName(ioFile.getName(), usedFileNames, null);
    zip.addFile(getZipPath(pluginName, fileName), ioFile);
  }

  private static void makeAndAddLibraryJar(VirtualFile root,
                                           String pluginName,
                                           Compressor zip,
                                           Set<String> usedJarNames,
                                           @Nullable String preferredName) throws IOException {
    File tempFile = FileUtil.createTempFile(TEMP_PREFIX, JAR_EXTENSION);
    try {
      try (Compressor tempZip = new Compressor.Zip(tempFile)) {
        FileTypeManager manager = FileTypeManager.getInstance();
        tempZip.filter((entryName, file) -> !manager.isFileIgnored(PathUtil.getFileName(entryName)));
        tempZip.addDirectory(VfsUtilCore.virtualToIoFile(root));
      }
      String jarName = getLibraryFileName(root.getName() + JAR_EXTENSION, usedJarNames, preferredName == null ? null : preferredName + JAR_EXTENSION);
      zip.addFile(getZipPath(pluginName, jarName), tempFile);
    }
    finally {
      FileUtil.delete(tempFile);
    }
  }

  private static String getLibraryFileName(String fileName, Set<String> usedFileNames, @Nullable String preferredName) {
    String uniqueName;
    if (preferredName != null && !usedFileNames.contains(preferredName)) {
      uniqueName = preferredName;
    }
    else {
      uniqueName = fileName;
      if (usedFileNames.contains(uniqueName)) {
        int dotPos = uniqueName.lastIndexOf('.');
        String name = dotPos < 0 ? uniqueName : uniqueName.substring(0, dotPos);
        String ext = dotPos < 0 ? "" : uniqueName.substring(dotPos);
        int i = 0;
        do {
          i++;
          uniqueName = name + i + ext;
        }
        while (usedFileNames.contains(uniqueName));
      }
    }
    usedFileNames.add(uniqueName);
    return uniqueName;
  }

  private static File preparePluginsJar(Module module, Set<Module> modules) throws IOException {
    PluginBuildConfiguration configuration = PluginBuildConfiguration.getInstance(module);
    Manifest manifest = createOrFindManifest(configuration);
    return jarModulesOutput(modules, manifest, configuration != null ? configuration.getPluginXmlPath() : null);
  }

  private static File jarModulesOutput(Set<Module> modules, @Nullable Manifest manifest, @Nullable String pluginXmlPath) throws IOException {
    File tempFile = FileUtil.createTempFile(TEMP_PREFIX, JAR_EXTENSION);

    try (Compressor.Jar jar = new Compressor.Jar(tempFile)) {
      FileTypeManager manager = FileTypeManager.getInstance();
      Set<String> uniqueEntries = new HashSet<>();
      jar.filter((entryName, file) -> !manager.isFileIgnored(PathUtil.getFileName(entryName)) && uniqueEntries.add(entryName));

      if (manifest != null) {
        jar.addManifest(manifest);
      }

      for (Module module : modules) {
        CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
        if (extension != null) {
          VirtualFile outputPath = extension.getCompilerOutputPath();
          if (outputPath != null) {
            // pre-condition: output dirs for all modules are up-to-date
            jar.addDirectory(new File(outputPath.getPath()));
          }
        }
      }

      if (pluginXmlPath != null) {
        jar.addFile(PluginDescriptorConstants.PLUGIN_XML_PATH, new File(pluginXmlPath));
      }
    }

    return tempFile;
  }

  public static Manifest createOrFindManifest(@Nullable PluginBuildConfiguration configuration) throws IOException {
    Manifest manifest = new Manifest();
    VirtualFile vManifest;
    if (configuration != null && configuration.isUseUserManifest() && (vManifest = configuration.getManifest()) != null) {
      try (InputStream in = new BufferedInputStream(vManifest.getInputStream())) {
        manifest.read(in);
      }
    }
    else {
      ManifestBuilder.setGlobalAttributes(manifest.getMainAttributes());
    }
    return manifest;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Module module = e.getData(PlatformCoreDataKeys.MODULE);
    boolean enabled = module != null && PluginModuleType.isOfType(module);
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      e.getPresentation().setText(DevKitBundle.messagePointer("prepare.for.deployment", module.getName()));
    }
  }
}