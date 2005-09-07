/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.j2ee.make.ManifestBuilder;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

/**
 * User: anna
 * Date: May 5, 2005
 */
public class PrepareToDeployAction extends AnAction {
  @NonNls private static final String ZIP_EXTENSION = ".zip";
  @NonNls private static final String JAR_EXTENSION = ".jar";
  @NonNls private static final String TEMP_PREFIX = "temp";
  @NonNls private static final String MIDDLE_LIB_DIR = "/lib/";

  public void actionPerformed(final AnActionEvent e) {
    final Module module = (Module)e.getDataContext().getData(DataConstants.MODULE);
    if (module == null) return;
    final String name = module.getName();
    final String defaultPath = new File(module.getModuleFilePath()).getParent() + File.separator + name;
    final HashSet<Module> modules = new HashSet<Module>();
    PluginBuildUtil.getDependencies(module, modules);
    modules.add(module);
    final HashSet<Library> libs = new HashSet<Library>();
    for (Module module1 : modules) {
      PluginBuildUtil.getLibraries(module1, libs);
    }
    boolean isZip = true;
    final String zipPath = defaultPath + ZIP_EXTENSION;
    final File zipFile = new File(zipPath);
    if (libs.size() == 0) {
      if (zipFile.exists()) {
        if (Messages.showYesNoDialog(module.getProject(), DevKitBundle.message("suggest.to.delete", zipPath), DevKitBundle.message("info.message"),
                                     Messages.getInformationIcon()) == DialogWrapper.OK_EXIT_CODE) {
          FileUtil.delete(zipFile);
        }
      }
      isZip = false;
    } else
      if (new File(defaultPath + JAR_EXTENSION).exists()) {
        if (Messages.showYesNoDialog(module.getProject(), DevKitBundle.message("suggest.to.delete", defaultPath + JAR_EXTENSION), DevKitBundle.message("info.message"),
                                     Messages.getInformationIcon()) == DialogWrapper.OK_EXIT_CODE) {
          FileUtil.delete(new File(defaultPath + JAR_EXTENSION));
        }
      }

    @NonNls final Set<String> errorSet = new HashSet<String>();
    final boolean isOk = ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null){
          progressIndicator.setText(DevKitBundle.message("prepare.for.deployment.common"));
          progressIndicator.setIndeterminate(true);
        }
        try {
          File jarFile = preparePluginsJar(module, modules);
          if (libs.size() == 0) {
            FileUtil.copy(jarFile, new File(defaultPath + JAR_EXTENSION));
            return;
          }

          if (zipFile.exists() || zipFile.createNewFile()) {
            final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
            ZipUtil.addFileToZip(zos, jarFile, "/" + name + MIDDLE_LIB_DIR + name + JAR_EXTENSION, new HashSet<String>(), new FileFilter() {
              public boolean accept(File pathname) {
                if (progressIndicator != null){
                  progressIndicator.setText2("");
                }
                return true;
              }
            });
            Set<String> names = new HashSet<String>();
            for (Library library : libs) {
              String libraryName = null;
              final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
              final HashSet<String> libraryWrittenItems = new HashSet<String>();
              File libraryJar = FileUtil.createTempFile(TEMP_PREFIX, JAR_EXTENSION);
              libraryJar.deleteOnExit();
              ZipOutputStream jar = null;
              for (VirtualFile virtualFile : files) {
                File ioFile = VfsUtil.virtualToIoFile(virtualFile);
                if (!(virtualFile.getFileSystem() instanceof JarFileSystem)) {
                  if (jar == null) {
                    jar = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(libraryJar)));
                  }
                  ZipUtil.addFileOrDirRecursively(jar, libraryJar, VfsUtil.virtualToIoFile(virtualFile), "", new FileFilter() {
                    public boolean accept(File pathname) {
                      if (progressIndicator != null){
                        progressIndicator.setText2("");
                      }
                      return true;
                    }
                  }, libraryWrittenItems);
                  if (libraryName == null) {
                    libraryName = library.getName() != null ? library.getName() : virtualFile.getName();
                    if (names.contains(libraryName)) {
                      int i = 1;
                      while (true) {
                        if (!names.contains(libraryName + i)) {
                          libraryName = libraryName + i;
                          break;
                        }
                        i++;
                      }
                    }
                    names.add(libraryName);
                  }
                }
                else {
                  ZipUtil.addFileOrDirRecursively(zos, jarFile, ioFile, "/" + name + MIDDLE_LIB_DIR + ioFile.getName(), new FileFilter() {
                    public boolean accept(File pathname) {
                      if (progressIndicator != null){
                        progressIndicator.setText2("");
                      }
                      return true;
                    }
                  }, new HashSet<String>());
                }
              }
              if (libraryName != null) {
                jar.close();
                ZipUtil.addFileOrDirRecursively(zos, jarFile, libraryJar, "/" + name + MIDDLE_LIB_DIR + libraryName + JAR_EXTENSION, new FileFilter() {
                  public boolean accept(File pathname) {
                    if (progressIndicator != null){
                      progressIndicator.setText2("");
                    }
                    return true;
                  }
                }, new HashSet<String>());
              }
            }
            zos.close();
          }
        }
        catch (final IOException e1) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              errorSet.add("error");
              Messages.showErrorDialog(e1.getMessage(), DevKitBundle.message("error.occured"));
            }
          }, ModalityState.NON_MMODAL);
        }
      }
    }, DevKitBundle.message("prepare.for.deployment", module.getName()), true, module.getProject());

    if (isOk && errorSet.isEmpty()) {
      Messages.showInfoMessage( DevKitBundle.message("saved.message", isZip ? 1 : 2, module.getName(), defaultPath + (isZip ? ZIP_EXTENSION : JAR_EXTENSION)) , DevKitBundle.message("success.deployment.message", module.getName()));
    }
  }

  private File preparePluginsJar(Module module, final HashSet<Module> modules) throws IOException {
    final PluginModuleBuildProperties pluginModuleBuildProperties = ((PluginModuleBuildProperties)ModuleBuildProperties
      .getInstance(module));
    File jarFile = FileUtil.createTempFile(TEMP_PREFIX, JAR_EXTENSION);
    jarFile.deleteOnExit();
    final Manifest manifest = new Manifest();

    if (pluginModuleBuildProperties.isUseUserManifest() && pluginModuleBuildProperties.getManifestPath() != null) {
      InputStream in = new BufferedInputStream(new FileInputStream(new File(pluginModuleBuildProperties.getManifestPath())));
      manifest.read(in);
      in.close();
    }
    else {
      Attributes mainAttributes = manifest.getMainAttributes();
      ManifestBuilder.setGlobalAttributes(mainAttributes);
    }
    ZipOutputStream jarPlugin = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)), manifest);

    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    final HashSet<String> writtenItemRelativePaths = new HashSet<String>();
    for (Module module1 : modules) {
      final VirtualFile compilerOutputPath = ModuleRootManager.getInstance(module1).getCompilerOutputPath();
      if (compilerOutputPath == null) continue; //pre-condition: output dirs for all modules are up-to-date
      ZipUtil.addDirToZipRecursively(jarPlugin, jarFile, new File(compilerOutputPath.getPath()), "", new FileFilter() {
        public boolean accept(File pathname) {
          if (progressIndicator != null){
            progressIndicator.setText2("");
          }
          return true;
        }
      }, writtenItemRelativePaths);
    }
    final String pluginXmlPath = pluginModuleBuildProperties.getPluginXmlPath();
    @NonNls final String metaInfDir = "/META-INF/plugin.xml";
    ZipUtil.addFileToZip(jarPlugin,
                         new File(pluginXmlPath),
                         metaInfDir,
                         writtenItemRelativePaths,
                         new FileFilter() {
                           public boolean accept(File pathname) {
                             if (progressIndicator != null){
                               progressIndicator.setText2("");
                             }
                             return true;
                           }
                         });
    jarPlugin.close();
    return jarFile;
  }

  public void update(AnActionEvent e) {
    final Module module = (Module)e.getDataContext().getData(DataConstants.MODULE);
    boolean enabled = module != null && module.getModuleType() instanceof PluginModuleType;
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
    if (enabled) {
      e.getPresentation().setText(DevKitBundle.message("prepare.for.deployment", module.getName()));
    }
  }
}
