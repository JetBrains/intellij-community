/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectMacrosUtil;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import icons.EclipseIcons;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.conversion.EclipseUserLibrariesHelper;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class EclipseImportBuilder extends ProjectImportBuilder<String> implements EclipseProjectWizardContext {
  private static final Logger LOG = Logger.getInstance("#" + EclipseImportBuilder.class.getName());

  public static class Parameters {
    public String root;
    public List<String> workspace;
    public boolean linkConverted;
    public List<String> projectsToConvert = new ArrayList<>();
    public boolean openModuleSettings;
    public Options converterOptions = new Options();
    public Set<String> existingModuleNames;
  }

  private Parameters parameters;


  @NotNull
  public String getName() {
    return EclipseBundle.message("eclipse.name");
  }

  public Icon getIcon() {
    return EclipseIcons.Eclipse;
  }

  @Nullable
  public String getRootDirectory() {
    return getParameters().root;
  }

  public boolean setRootDirectory(final String path) {
    ProgressManager.getInstance().run(new Task.Modal(getCurrentProject(), EclipseBundle.message("eclipse.import.scanning"), true) {
      public void run(@NotNull ProgressIndicator indicator) {
        final ArrayList<String> roots = new ArrayList<>();
        EclipseProjectFinder.findModuleRoots(roots, path, path12 -> {
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return false;
            progressIndicator.setText2(path12);
          }
          return true;
        });
        Collections.sort(roots, (path1, path2) -> {
          final String projectName1 = EclipseProjectFinder.findProjectName(path1);
          final String projectName2 = EclipseProjectFinder.findProjectName(path2);
          return projectName1 != null && projectName2 != null ? projectName1.compareToIgnoreCase(projectName2) : 0;
        });
        getParameters().workspace = roots;
        getParameters().root = path;
      }

      public void onCancel() {
        getParameters().workspace = null;
        getParameters().root = null;
      }
    });

    setFileToImport(path);
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
    final Ref<Exception> refEx = new Ref<>();
    final Set<String> variables = new THashSet<>();
    final Map<String, String> naturesNames = new THashMap<>();
    final List<String> projectsToConvert = getParameters().projectsToConvert;
    final boolean oneProjectToConvert = projectsToConvert.size() == 1;
    final String separator = oneProjectToConvert ? "<br>" : ", ";
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        for (String path : projectsToConvert) {
          File classPathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
          if (classPathFile.exists()) {
            EclipseClasspathReader.collectVariables(variables, JDOMUtil.load(classPathFile), path);
          }
          collectUnknownNatures(path, naturesNames, separator);
        }
      }
      catch (IOException e) {
        refEx.set(e);
      }
      catch (JDOMException e) {
        refEx.set(e);
      }
    }, EclipseBundle.message("eclipse.import.converting"), false, currentProject);

    if (!refEx.isNull()) {
      Messages.showErrorDialog(dstProject, refEx.get().getMessage(), getTitle());
      return false;
    }

    if (!ProjectMacrosUtil.checkNonIgnoredMacros(dstProject, variables)) {
      return false;
    }

    if (!naturesNames.isEmpty()) {
      final String title = "Unknown Natures Detected";
      final String naturesByProject;
      if (oneProjectToConvert) {
        naturesByProject = naturesNames.values().iterator().next();
      }
      else {
        naturesByProject = StringUtil.join(naturesNames.keySet(), projectPath -> projectPath + "(" + naturesNames.get(projectPath) + ")", "<br>");
      }
      Notifications.Bus.notify(new Notification(title, title, "Imported projects contain unknown natures:<br>" + naturesByProject + "<br>" +
                                                              "Some settings may be lost after import.", NotificationType.WARNING));
    }

    return true;
  }

  @Override
  public List<Module> commit(final Project project, ModifiableModuleModel model, ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {

    final Collection<String> unknownLibraries = new TreeSet<>();
    final Collection<String> unknownJdks = new TreeSet<>();
    final Set<String> refsToModules = new HashSet<>();
    final List<Module> result = new ArrayList<>();
    final Map<Module, Set<String>> module2NatureNames = new HashMap<>();

    try {
      final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
      final ModifiableRootModel[] rootModels = new ModifiableRootModel[getParameters().projectsToConvert.size()];
      final Set<File> files = new HashSet<>();
      final Set<String> moduleNames = new THashSet<>(getParameters().projectsToConvert.size());
      for (String path : getParameters().projectsToConvert) {
        String modulesDirectory = getParameters().converterOptions.commonModulesDirectory;
        if (modulesDirectory == null) {
          modulesDirectory = path;
        }
        final String moduleName = EclipseProjectFinder.findProjectName(path);
        moduleNames.add(moduleName);
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
        final int resultCode = Messages.showYesNoCancelDialog(ApplicationNamesInfo.getInstance().getFullProductName() +
                                                              " module files found:\n" +
                                                              StringUtil.join(files, file -> file.getPath(), "\n") +
                                                              ".\n Would you like to reuse them?", "Module Files Found",
                                                              Messages.getQuestionIcon());
        if (resultCode != Messages.YES) {
          if (resultCode == Messages.NO) {
            final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            for (File file : files) {
              final VirtualFile virtualFile = localFileSystem.findFileByIoFile(file);
              if (virtualFile != null) {
                ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Void, IOException>() {
                  @Override
                  public Void compute() throws IOException {
                    virtualFile.delete(this);
                    return null;
                  }
                });
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
      for (String path : getParameters().projectsToConvert) {
        String modulesDirectory = getParameters().converterOptions.commonModulesDirectory;
        if (modulesDirectory == null) {
          modulesDirectory = path;
        }
        final Module module = moduleModel.newModule(modulesDirectory + "/" + EclipseProjectFinder.findProjectName(path) + IdeaXml.IML_EXT,
                                                    StdModuleTypes.JAVA.getId());
        result.add(module);
        final Set<String> natures = collectNatures(path);

        if (natures.size() > 0) {
          module2NatureNames.put(module, natures);
        }
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        rootModels[idx++] = rootModel;

        final File classpathFile = new File(path, EclipseXml.DOT_CLASSPATH_EXT);
        final EclipseClasspathReader classpathReader = new EclipseClasspathReader(path, project, getParameters().projectsToConvert, moduleNames);
        classpathReader.init(rootModel);
        if (classpathFile.exists()) {
          Element classpathElement = JDOMUtil.load(classpathFile);
          classpathReader.readClasspath(rootModel, unknownLibraries, unknownJdks, refsToModules,
                                        getParameters().converterOptions.testPattern, classpathElement);
        }
        else {
          EclipseClasspathReader.setOutputUrl(rootModel, path + "/bin");
        }
        ClasspathStorage.setStorageType(rootModel,
                                        getParameters().linkConverted ? JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID : ClassPathStorageUtil.DEFAULT_STORAGE);
        if (model != null) {
          ApplicationManager.getApplication().runWriteAction(() -> rootModel.commit());
        }
      }
      if (model == null) {
        ApplicationManager.getApplication().runWriteAction(() -> ModifiableModelCommitter.multiCommit(rootModels, moduleModel));
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    scheduleNaturesImporting(project, module2NatureNames);
    createEclipseLibrary(project, unknownLibraries, IdeaXml.ECLIPSE_LIBRARY);

    StringBuilder message = new StringBuilder();
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
      final StringBuilder buf = new StringBuilder();
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
        final VirtualFile selectedFile = FileChooser.chooseFile(descriptor, project, project.getBaseDir());
        if (selectedFile != null) {
          try {
            EclipseUserLibrariesHelper.readProjectLibrariesContent(selectedFile, project, unknownLibraries);
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }
    }

    if (message.length() > 0) {
      Messages.showErrorDialog(project, message.toString(), getTitle());
    }

    return result;
  }

  private static void scheduleNaturesImporting(@NotNull final Project project,
                                               @NotNull final Map<Module, Set<String>> module2NatureNames) {
    if (module2NatureNames.size() == 0) {
      return;
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> DumbService.getInstance(project).smartInvokeLater(() -> {
      for (EclipseNatureImporter importer : EclipseNatureImporter.EP_NAME.getExtensions()) {
        final String importerNatureName = importer.getNatureName();
        final List<Module> modulesToImport = new ArrayList<>();

        for (Map.Entry<Module, Set<String>> entry : module2NatureNames.entrySet()) {
          final Module module = entry.getKey();
          final Set<String> natureNames = entry.getValue();

          if (natureNames.contains(importerNatureName)) {
            modulesToImport.add(module);
          }
        }

        if (modulesToImport.size() > 0) {
          importer.doImport(project, modulesToImport);
        }
      }
    }));
  }

  private static void createEclipseLibrary(final Project project, final Collection<String> libraries, final String libraryName) {
    if (libraries.contains(libraryName)) {
      final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {

        public Icon getIcon(final VirtualFile file) {
          return looksLikeEclipse(file) ? dressIcon(file, EclipseIcons.Eclipse) : super.getIcon(file);
        }

        private boolean looksLikeEclipse(final VirtualFile file) {
          return file.findChild(".eclipseproduct") != null;
        }
      };
      fileChooserDescriptor.setTitle(EclipseBundle.message("eclipse.create.library.title"));
      fileChooserDescriptor.setDescription(EclipseBundle.message("eclipse.create.library.description", libraryName));
      final VirtualFile file = FileChooser.chooseFile(fileChooserDescriptor, project, null);
      if (file != null) {
        final VirtualFile pluginsDir = file.findChild("plugins");
        if (pluginsDir != null) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            final LibraryTable table =
              LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(LibraryTablesRegistrar.APPLICATION_LEVEL, project);
            assert table != null;
            final LibraryTable.ModifiableModel tableModel = table.getModifiableModel();
            final Library library = tableModel.createLibrary(libraryName);
            final Library.ModifiableModel libraryModel = library.getModifiableModel();
            libraryModel.addJarDirectory(pluginsDir, true);
            libraryModel.commit();
            tableModel.commit();
          });
          libraries.remove(libraryName);
        }
      }
    }
  }

  @NotNull
  public Parameters getParameters() {
    if (parameters == null) {
      parameters = new Parameters();
      parameters.existingModuleNames = new THashSet<>();
      if (isUpdate()) {
        Project project = getCurrentProject();
        if (project != null) {
          for (Module module : ModuleManager.getInstance(project).getModules()) {
            parameters.existingModuleNames.add(module.getName());
          }
        }
      }
    }
    return parameters;
  }

  public static void collectUnknownNatures(String path, Map<String, String> naturesNames, String separator) {
    Set<String> natures = collectNatures(path);
    natures.removeAll(EclipseNatureImporter.getDefaultNatures());

    for (EclipseNatureImporter importer : EclipseNatureImporter.EP_NAME.getExtensions()) {
      natures.remove(importer.getNatureName());
    }

    if (!natures.isEmpty()) {
      naturesNames.put(path, StringUtil.join(natures, separator));
    }
  }

  @NotNull
  public static Set<String> collectNatures(@NotNull String path) {
    Set<String> naturesNames = new THashSet<>();
    try {
      Element natures = JDOMUtil.load(new File(path, EclipseXml.DOT_PROJECT_EXT)).getChild("natures");
      if (natures != null) {
        for (Element nature : natures.getChildren("nature")) {
          String natureName = nature.getText();
          if (!StringUtil.isEmptyOrSpaces(natureName)) {
            naturesNames.add(natureName);
          }
        }
      }
    }
    catch (Exception ignore) {
    }
    return naturesNames;
  }
}
