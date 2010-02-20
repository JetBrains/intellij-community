/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.Function;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseClasspathStorageProvider;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.conversion.EclipseUserLibrariesHelper;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class EclipseImportBuilder extends ProjectImportBuilder<String> implements EclipseProjectWizardContext {
  private static final Icon eclipseIcon = IconLoader.getIcon("/images/eclipse.gif");
  private static final Logger LOG = Logger.getInstance("#" + EclipseImportBuilder.class.getName());

  public static class Parameters {
    public String root;
    public List<String> workspace;
    public boolean linkConverted;
    public List<String> projectsToConvert = new ArrayList<String>();
    public boolean openModuleSettings;
    public Options converterOptions = new Options();
    public Set<String> existingModuleNames;
  }

  private Parameters parameters;


  public String getName() {
    return EclipseBundle.message("eclipse.name");
  }

  public Icon getIcon() {
    return eclipseIcon;
  }

  @Nullable
  public String getRootDirectory() {
    return getParameters().root;
  }

  public boolean setRootDirectory(final String path) {
    ProgressManager.getInstance().run(new Task.Modal(getCurrentProject(), EclipseBundle.message("eclipse.import.scanning"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        final ArrayList<String> roots = new ArrayList<String>();
        EclipseProjectFinder.findModuleRoots(roots, path);
        getParameters().workspace = roots;
        getParameters().root = path;
      }

      public void onCancel() {
        getParameters().workspace = null;
        getParameters().root = null;
      }
    });

    return getParameters().workspace != null;
  }

  public List<String> getList() {
    return getParameters().workspace;
  }

  public boolean isMarked(final String element) {
    if (getParameters().projectsToConvert != null) {
      return getParameters().projectsToConvert.contains(element);
    }
    return !getParameters().existingModuleNames.contains(EclipseProjectFinder.findProjectName(element));
  }

  public void setList(List<String> list) {
    getParameters().projectsToConvert = list;
  }

  public boolean isOpenProjectSettingsAfter() {
    return getParameters().openModuleSettings;
  }

  public void setOpenProjectSettingsAfter(boolean on) {
    getParameters().openModuleSettings = on;
  }

  public void cleanup() {
    super.cleanup();
    parameters = null;
  }

  public boolean validate(final Project currentProject, final Project dstProject) {
    final Ref<Exception> refEx = new Ref<Exception>();
    final HashSet<String> variables = new HashSet<String>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          for (String path : getParameters().projectsToConvert) {
            final File classpathfile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
            if (classpathfile.exists()) {
              final Element classpathElement = JDOMUtil.loadDocument(classpathfile).getRootElement();
              EclipseClasspathReader.collectVariables(variables, classpathElement);
            }
          }
        }
        catch (IOException e) {
          refEx.set(e);
        }
        catch (JDOMException e) {
          refEx.set(e);
        }
      }
    }, EclipseBundle.message("eclipse.import.converting"), false, currentProject);

    if (!refEx.isNull()) {
      Messages.showErrorDialog(dstProject, refEx.get().getMessage(), getTitle());
      return false;
    }

    if (!ProjectMacrosUtil.checkMacros(dstProject, variables)) {
      return false;
    }

    return true;
  }

  @Override
  public List<Module> commit(final Project project, ModifiableModuleModel model, ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {

    final Collection<String> unknownLibraries = new TreeSet<String>();
    final Collection<String> unknownJdks = new TreeSet<String>();
    final Set<String> refsToModules = new HashSet<String>();
    final List<Module> result = new ArrayList<Module>();

    try {
      final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
      final ModifiableRootModel[] rootModels = new ModifiableRootModel[getParameters().projectsToConvert.size()];
      final Set<File> files = new HashSet<File>();
      for (String path : getParameters().projectsToConvert) {
        String modulesDirectory = getParameters().converterOptions.commonModulesDirectory;
        if (modulesDirectory == null) {
          modulesDirectory = path;
        }
        final String moduleName = EclipseProjectFinder.findProjectName(path);
        final File imlFile = new File(modulesDirectory + File.separator + moduleName + IdeaXml.IML_EXT);
        if (imlFile.isFile()) {
          files.add(imlFile);
        }
        final File emlFile = new File(modulesDirectory + File.separator + moduleName + EclipseXml.IDEA_SETTINGS_POSTFIX);
        if (emlFile.isFile()) {
          files.add(emlFile);
        }
      }
      if (!files.isEmpty()) {
        final int resultCode = Messages.showYesNoCancelDialog(ApplicationInfoEx.getInstanceEx().getFullApplicationName() +
                                                              " module files found:\n" +
                                                              StringUtil.join(files,new Function<File, String>() {
                                                                public String fun(File file) {
                                                                  return file.getPath();
                                                                }
                                                              }, "\n") +
                                                              ".\n Would you like to reuse them?", "Module files found",
                                                              Messages.getQuestionIcon());
        if (resultCode != DialogWrapper.OK_EXIT_CODE) {
          if (resultCode == DialogWrapper.CANCEL_EXIT_CODE) {
            final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            for (File file : files) {
              final VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
              if (virtualFile != null) {
                final IOException[] ex = new IOException[1];
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  public void run() {
                    try {
                      virtualFile.delete(this);
                    }
                    catch (IOException e) {
                      ex[0] = e;
                    }
                  }
                });
                if (ex[0] != null) {
                  throw ex[0];
                }
              }
              else {
                FileUtil.delete(file);
              }
            }
          } else {
            return result;
          }
        }
      }
      int idx = 0;
      final Set<String> usedVariables = new HashSet<String>();
      for (String path : getParameters().projectsToConvert) {
        String modulesDirectory = getParameters().converterOptions.commonModulesDirectory;
        if (modulesDirectory == null) {
          modulesDirectory = path;
        }
        final Module module = moduleModel.newModule(modulesDirectory + "/" + EclipseProjectFinder.findProjectName(path) + IdeaXml.IML_EXT, StdModuleTypes.JAVA);
        result.add(module);
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        rootModels[idx++] = rootModel;

        final File classpathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
        final EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, project, getParameters().projectsToConvert);
        classpathReader.init(rootModel);
        if (classpathFile.exists()) {
          final Element classpathElement = JDOMUtil.loadDocument(classpathFile).getRootElement();
          classpathReader.readClasspath(rootModel, unknownLibraries, unknownJdks, usedVariables, refsToModules,
                                                                  getParameters().converterOptions.testPattern, classpathElement);
        } else {
          EclipseClasspathReader.setupOutput(rootModel, path + "/bin");
        }
        ClasspathStorage.setStorageType(rootModel,
                                      getParameters().linkConverted ? EclipseClasspathStorageProvider.ID : ClasspathStorage.DEFAULT_STORAGE);
        if (model != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              rootModel.commit();
            }
          });
        }
      }
      if (model == null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run(){
              ProjectRootManagerEx.getInstanceEx(project).multiCommit(moduleModel, rootModels);

            }
        });
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }

    createEclipseLibrary(project, unknownLibraries, IdeaXml.ECLIPSE_LIBRARY);

    StringBuffer message = new StringBuffer();
    refsToModules.removeAll(getParameters().existingModuleNames);
    for (String path : getParameters().projectsToConvert) {
      final String projectName = EclipseProjectFinder.findProjectName(path);
      if (projectName != null) {
        refsToModules.remove(projectName);
        getParameters().existingModuleNames.add(projectName);
      }
    }
    if (!refsToModules.isEmpty()) {

      message.append("Unknown modules detected");
      for (String module : refsToModules) {
        message.append("\n").append(module);
      }
    }
    if (!unknownJdks.isEmpty()) {
      if (message.length() > 0){
        message.append("\nand jdks");
      } else {
        message.append("Imported project refers to unknown jdks");
      }
      for (String unknownJdk : unknownJdks) {
        message.append("\n").append(unknownJdk);
      }
    }
    if (!unknownLibraries.isEmpty()) {
      final StringBuffer buf = new StringBuffer();
      buf.append("<html><body>");
      buf.append(EclipseBundle.message("eclipse.import.warning.undefinded.libraries"));
      for (String name : unknownLibraries) {
        buf.append("<br>").append(name);
      }
      if (model == null) {
        buf.append("<br><b>Please export Eclipse user libraries and import them now from resulted .userlibraries file</b>");
        buf.append("</body></html>");
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
          @Override
          public boolean isFileSelectable(VirtualFile file) {
            return super.isFileSelectable(file) && Comparing.strEqual(file.getExtension(), "userlibraries");
          }
        };
        descriptor.setDescription(buf.toString());
        descriptor.setTitle(getTitle());
        final VirtualFile[] selectedFiles = FileChooser.chooseFiles(project, descriptor, project.getBaseDir());
        if (selectedFiles.length == 1) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                EclipseUserLibrariesHelper.readProjectLibrariesContent(new File(selectedFiles[0].getPath()), project, unknownLibraries);
              }
              catch (Exception e) {
                LOG.error(e);
              }
            }
          });
        }
      }
    }

    if (message.length() > 0) {
      Messages.showErrorDialog(project, message.toString(), getTitle());
    }

    return result;
  }

  private static void createEclipseLibrary(final Project project, final Collection<String> libraries, final String libraryName) {
    if (libraries.contains(libraryName)) {
      final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
        public Icon getOpenIcon(final VirtualFile virtualFile) {
          return looksLikeEclipse(virtualFile) ? eclipseIcon : super.getOpenIcon(virtualFile);
        }

        public Icon getClosedIcon(final VirtualFile virtualFile) {
          return looksLikeEclipse(virtualFile) ? eclipseIcon : super.getClosedIcon(virtualFile);
        }

        private boolean looksLikeEclipse(final VirtualFile virtualFile) {
          return virtualFile.findChild(".eclipseproduct") != null;
        }
      };
      fileChooserDescriptor.setTitle(EclipseBundle.message("eclipse.create.library.title"));
      fileChooserDescriptor.setDescription(EclipseBundle.message("eclipse.create.library.description", libraryName));
      final VirtualFile[] files = FileChooser.chooseFiles(project, fileChooserDescriptor);
      if (files.length == 1) {
        final VirtualFile pluginsDir = files[0].findChild("plugins");
        if (pluginsDir != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              final LibraryTable table =
                LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(LibraryTablesRegistrar.APPLICATION_LEVEL, project);
              assert table != null;
              final LibraryTable.ModifiableModel tableModel = table.getModifiableModel();
              final Library library = tableModel.createLibrary(libraryName);
              final Library.ModifiableModel libraryModel = library.getModifiableModel();
              libraryModel.addJarDirectory(pluginsDir, true);
              libraryModel.commit();
              tableModel.commit();
            }
          });
          libraries.remove(libraryName);
        }
      }
    }
  }

  public Parameters getParameters() {
    if (parameters == null) {
      parameters = new Parameters();
      parameters.existingModuleNames = new HashSet<String>();
      if (isUpdate()) {
        for (Module module : ModuleManager.getInstance(getCurrentProject()).getModules()) {
          parameters.existingModuleNames.add(module.getName());
        }
      }
    }
    return parameters;
  }
}
