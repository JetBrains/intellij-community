// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.application.options.CodeStyle;
import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerEx;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.conversion.EclipseClasspathReader;
import org.jetbrains.idea.eclipse.conversion.EclipseUserLibrariesHelper;
import org.jetbrains.idea.eclipse.importer.EclipseProjectCodeStyleData;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class EclipseImportBuilder extends ProjectImportBuilder<String> implements EclipseProjectWizardContext {
  private static final Logger LOG = Logger.getInstance(EclipseImportBuilder.class);

  public static class Parameters {
    public String root;
    public List<String> workspace;
    public boolean linkConverted;
    public List<String> projectsToConvert = new ArrayList<>();
    public boolean openModuleSettings;
    public Options converterOptions = new Options();
    public Set<String> existingModuleNames;
    public @Nullable EclipseProjectCodeStyleData codeStyleData;
  }

  private Parameters parameters;

  @Override
  public @NotNull String getName() {
    return EclipseBundle.message("eclipse.name");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Providers.Eclipse;
  }

  @Override
  public @Nullable String getRootDirectory() {
    return getParameters().root;
  }

  @Override
  public boolean setRootDirectory(@NotNull String path) {
    ProgressManager.getInstance().run(new Task.Modal(getCurrentProject(), EclipseBundle.message("eclipse.import.scanning"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        List<String> roots = new ArrayList<>();
        EclipseProjectFinder.findModuleRoots(roots, path, (@NlsSafe var path12) -> {
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return false;
            progressIndicator.setText2(path12);
          }
          return true;
        });
        roots.sort((path1, path2) -> {
          final String projectName1 = EclipseProjectFinder.findProjectName(path1);
          final String projectName2 = EclipseProjectFinder.findProjectName(path2);
          return projectName1 != null && projectName2 != null ? projectName1.compareToIgnoreCase(projectName2) : 0;
        });
        getParameters().workspace = roots;
        getParameters().root = path;
      }

      @Override
      public void onCancel() {
        getParameters().workspace = null;
        getParameters().root = null;
      }
    });

    setFileToImport(path);
    return getParameters().workspace != null;
  }

  @Override
  public List<String> getList() {
    return getParameters().workspace;
  }

  @Override
  public boolean isMarked(final String element) {
    if (getParameters().projectsToConvert != null) {
      return getParameters().projectsToConvert.contains(element);
    }
    return !getParameters().existingModuleNames.contains(EclipseProjectFinder.findProjectName(element));
  }

  @Override
  public void setList(List<String> list) {
    getParameters().projectsToConvert = list;
  }

  @Override
  public boolean isOpenProjectSettingsAfter() {
    return getParameters().openModuleSettings;
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
    getParameters().openModuleSettings = on;
  }

  @Override
  public void cleanup() {
    super.cleanup();
    parameters = null;
  }

  @Override
  public boolean validate(@Nullable Project currentProject, @NotNull Project project) {
    final Ref<Exception> refEx = new Ref<>();
    final Set<String> variables = new HashSet<>();
    final Map<String, String> naturesNames = new HashMap<>();
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
      catch (IOException | JDOMException e) {
        refEx.set(e);
      }
    }, EclipseBundle.message("eclipse.import.converting"), false, null);

    if (!refEx.isNull()) {
      Messages.showErrorDialog(project, refEx.get().getMessage(), getTitle());
      return false;
    }

    if (!ProjectMacrosUtil.checkNonIgnoredMacros(project, variables)) {
      return false;
    }

    if (!naturesNames.isEmpty()) {
      final String title = EclipseBundle.message("notification.title.unknown.natures.detected");
      final String naturesByProject;
      if (oneProjectToConvert) {
        naturesByProject = naturesNames.values().iterator().next();
      }
      else {
        naturesByProject = StringUtil.join(naturesNames.keySet(), projectPath -> projectPath + "(" + naturesNames.get(projectPath) + ")", "<br>");
      }
      Notifications.Bus.notify(new Notification("Unknown Natures Detected", title, EclipseBundle
        .message("notification.content.imported.projects.contain.unknown.natures",
                 naturesByProject), NotificationType.WARNING));
    }

    return true;
  }

  @Override
  public List<Module> commit(final Project project, ModifiableModuleModel model, ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel) {

    final Collection<@NlsSafe String> unknownLibraries = new TreeSet<>();
    final Collection<String> unknownJdks = new TreeSet<>();
    final Set<String> refsToModules = new HashSet<>();
    final List<Module> result = new ArrayList<>();
    final Map<Module, Set<String>> module2NatureNames = new HashMap<>();

    try {
      final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
      final ModifiableRootModel[] rootModels = new ModifiableRootModel[getParameters().projectsToConvert.size()];
      final Set<File> files = new HashSet<>();
      final Set<String> moduleNames = new HashSet<>(getParameters().projectsToConvert.size());
      for (String path : getParameters().projectsToConvert) {
        String modulesDirectory = getParameters().converterOptions.commonModulesDirectory;
        if (modulesDirectory == null) {
          modulesDirectory = path;
        }
        final String moduleName = EclipseProjectFinder.findProjectName(path);
        moduleNames.add(moduleName);
        final File imlFile = new File(modulesDirectory + File.separator + moduleName + ModuleManagerEx.IML_EXTENSION);
        if (imlFile.isFile()) {
          files.add(imlFile);
        }
        final File emlFile = new File(modulesDirectory + File.separator + moduleName + EclipseXml.IDEA_SETTINGS_POSTFIX);
        if (emlFile.isFile()) {
          files.add(emlFile);
        }
      }
      if (!files.isEmpty()) {
        final int resultCode = Messages.showYesNoCancelDialog(EclipseBundle.message(
          "dialog.message.0.module.files.found.1.would.you.like.to.reuse.them", ApplicationNamesInfo.getInstance().getFullProductName(),
          StringUtil.join(files, file -> file.getPath(), "\n")), EclipseBundle.message("dialog.title.module.files.found"),
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
        final Module module = moduleModel.newModule(modulesDirectory + "/" + EclipseProjectFinder.findProjectName(path) + ModuleManagerEx.IML_EXTENSION,
                                                    JavaModuleType.getModuleType().getId());
        result.add(module);
        final Set<String> natures = collectNatures(path);

        if (!natures.isEmpty()) {
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

    refsToModules.removeAll(getParameters().existingModuleNames);
    for (String path : getParameters().projectsToConvert) {
      final String projectName = EclipseProjectFinder.findProjectName(path);
      if (projectName != null) {
        refsToModules.remove(projectName);
        getParameters().existingModuleNames.add(projectName);
      }
    }
    @Nls StringBuilder message = new StringBuilder();
    if (!refsToModules.isEmpty()) {

      message.append(EclipseBundle.message("unknown.modules.detected.dialog.message", StringUtil.join(refsToModules, "\n")));
    }
    if (!unknownJdks.isEmpty()) {
      message.append(EclipseBundle.message("unknown.jdks.detected.message",
                                           !message.isEmpty() ? 0 : 1,
                                           StringUtil.join(unknownJdks, "\n")));
    }
    
    if (!unknownLibraries.isEmpty()) {
      final HtmlBuilder buf = new HtmlBuilder();
      buf.append(EclipseBundle.message("eclipse.import.warning.undefinded.libraries"));
      for (@NlsSafe String name : unknownLibraries) {
        buf.br().append(name);
      }
      if (model == null) {
        buf.br().append(HtmlChunk.text(EclipseBundle.message("unknown.libraries.dialog.description")).bold());
        
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
          @Override
          public boolean isFileSelectable(@Nullable VirtualFile file) {
            return super.isFileSelectable(file) && Comparing.strEqual(file.getExtension(), "userlibraries");
          }
        };
        descriptor.setDescription(buf.wrapWithHtmlBody().toString());
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

    setupProjectCodeStyle(project, message);

    if (!message.isEmpty()) {
      Messages.showErrorDialog(project, message.toString(), getTitle());
    }

    return result;
  }


  private void setupProjectCodeStyle(@NotNull Project project, @NotNull StringBuilder messageBuilder) {
    try {
      EclipseProjectCodeStyleData codeStyleData = getParameters().codeStyleData;
      if (codeStyleData != null) {
        CodeStyleSettings projectSettings = codeStyleData.importCodeStyle();
        if (projectSettings != null) {
          CodeStyle.setMainProjectSettings(project, projectSettings);
        }
      }
    }
    catch (Exception e) {
      if (!messageBuilder.isEmpty()) messageBuilder.append('\n');
      messageBuilder.append(EclipseBundle.message("error.while.importing.project.code.style", e.getMessage()));
    }
  }

  private static void scheduleNaturesImporting(final @NotNull Project project,
                                               final @NotNull Map<Module, Set<String>> module2NatureNames) {
    if (module2NatureNames.isEmpty()) {
      return;
    }
    StartupManager.getInstance(project).runAfterOpened(() -> {
      DumbService.getInstance(project).smartInvokeLater(() -> {
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

          if (!modulesToImport.isEmpty()) {
            importer.doImport(project, modulesToImport);
          }
        }
      });
    });
  }

  private static void createEclipseLibrary(final Project project, final Collection<String> libraries, final String libraryName) {
    if (libraries.contains(libraryName)) {
      final FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
        @Override
        public Icon getIcon(final VirtualFile file) {
          return looksLikeEclipse(file) ? dressIcon(file, AllIcons.Providers.Eclipse) : super.getIcon(file);
        }

        private static boolean looksLikeEclipse(final VirtualFile file) {
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

  public @NotNull Parameters getParameters() {
    if (parameters == null) {
      parameters = new Parameters();
      parameters.existingModuleNames = new HashSet<>();
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

  public static @NotNull Set<String> collectNatures(@NotNull String path) {
    Set<String> naturesNames = new HashSet<>();
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
