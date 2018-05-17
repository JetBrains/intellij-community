// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder;
import com.intellij.openapi.externalSystem.service.project.wizard.ExternalModuleSettingsStep;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ThreeState;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.service.settings.GradleProjectSettingsControl;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Vladislav.Soroka
 */
public class GradleProjectOpenProcessor extends ProjectOpenProcessor {

  @NotNull public static final String[] BUILD_FILE_EXTENSIONS = {GradleConstants.EXTENSION, GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION};

  @Override
  public String getName() {
    return GradleBundle.message("gradle.name");
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return GradleIcons.Gradle;
  }

  @Override
  public boolean canOpenProject(VirtualFile file) {
    if (file.isDirectory()) {
      return Arrays.stream(file.getChildren()).anyMatch(GradleProjectOpenProcessor::canOpenFile);
    }
    else {
      return canOpenFile(file);
    }
  }

  public static boolean canOpenFile(VirtualFile file) {
    return !file.isDirectory() && Arrays.stream(BUILD_FILE_EXTENSIONS).anyMatch(file.getName()::endsWith);
  }

  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    final WizardContext wizardContext = new WizardContext(null, null);
    if (virtualFile.isDirectory()) {
      for (VirtualFile file : virtualFile.getChildren()) {
        if (canOpenProject(file)) {
          virtualFile = file;
          break;
        }
      }
    }

    VirtualFile baseDir = virtualFile.getParent();
    String pathToOpen = baseDir.getPath();
    wizardContext.setProjectFileDirectory(pathToOpen);

    GradleAbstractWizard wizard = new GradleAbstractWizard(wizardContext, pathToOpen);
    AbstractExternalModuleBuilder<GradleProjectSettings> wizardBuilder = wizard.getBuilder();
    try {
      wizard.show();
      if (DialogWrapper.OK_EXIT_CODE == wizard.getExitCode()) {
        final Project projectToOpen;
        projectToOpen = ProjectManagerEx.getInstanceEx().newProject(wizardContext.getProjectName(), pathToOpen, true, false);
        if (projectToOpen == null) return null;
        if (!wizardBuilder.validate(projectToClose, projectToOpen)) {
          return null;
        }

        ExternalProjectsManagerImpl.getInstance(projectToOpen).setStoreExternally(true);
        VirtualFile finalVirtualFile = virtualFile;
        ExternalSystemApiUtil.subscribe(projectToOpen, GradleConstants.SYSTEM_ID, new GradleSettingsListenerAdapter() {
          @Override
          public void onProjectsLinked(@NotNull Collection<GradleProjectSettings> settings) {
            createProjectPreview(projectToOpen, pathToOpen, finalVirtualFile);
          }
        });
        wizardBuilder.commit(projectToOpen, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
        projectToOpen.save();

        if (!forceOpenInNewFrame) {
          closePreviousProject(projectToClose);
        }
        ProjectUtil.updateLastProjectLocation(pathToOpen);

        projectToOpen.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, Boolean.TRUE);
        ProjectManagerEx.getInstanceEx().openProject(projectToOpen);
        return projectToOpen;
      }
    }
    finally {
      wizardBuilder.cleanup();
    }
    return null;
  }

  public static void closePreviousProject(final Project projectToClose) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      int exitCode = ProjectUtil.confirmOpenNewProject(true);
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        ProjectUtil.closeAndDispose(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1]);
      }
    }
  }

  private static class GradleAbstractWizard extends AbstractWizard<ExternalModuleSettingsStep> {
    private AbstractExternalModuleBuilder<GradleProjectSettings> myBuilder;

    public GradleAbstractWizard(WizardContext wizardContext, String rootProjectPath) {
      super("Open Gradle Project", (Project)null);
      GradleProjectSettings gradleProjectSettings = createDefaultProjectSettings();
      myBuilder = new AbstractExternalModuleBuilder<GradleProjectSettings>(GradleConstants.SYSTEM_ID, gradleProjectSettings) {
        @Override
        protected void setupModule(Module module) throws ConfigurationException {
          super.setupModule(module);
          assert rootProjectPath != null;

          // it will be set later in any case, but save is called immediately after project creation, so, to ensure that it will be properly saved as external system module
          ExternalSystemModulePropertyManager.getInstance(module).setExternalId(GradleConstants.SYSTEM_ID);

          final Project project = module.getProject();
          FileDocumentManager.getInstance().saveAllDocuments();
          final GradleProjectSettings gradleProjectSettings = getExternalProjectSettings();
          Runnable runnable = () -> {
            gradleProjectSettings.setExternalProjectPath(rootProjectPath);
            AbstractExternalSystemSettings settings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID);
            //noinspection unchecked
            settings.linkProject(gradleProjectSettings);

            ImportSpec importSpec = new ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
              .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
              .useDefaultCallback()
              .build();
            ExternalSystemUtil.refreshProject(rootProjectPath, importSpec);
          };

          // execute when current dialog is closed
          ExternalSystemUtil.invokeLater(project, ModalityState.NON_MODAL, runnable);
        }

        @Override
        public void setupRootModel(ModifiableRootModel modifiableRootModel) {
        }

        @Override
        public ModuleType getModuleType() {
          return ModuleTypeManager.getInstance().getDefaultModuleType();
        }
      };
      GradleProjectSettingsControl settingsControl = new GradleProjectSettingsControl(myBuilder.getExternalProjectSettings());
      ExternalModuleSettingsStep<GradleProjectSettings> step =
        new ExternalModuleSettingsStep<GradleProjectSettings>(wizardContext, myBuilder, settingsControl) {
          @Override
          public void _commit(boolean finishChosen) throws CommitStepException {
            try {
              validate();
              updateDataModel();
            }
            catch (ConfigurationException e) {
              throw new CommitStepException(e.getMessage());
            }
          }
        };
      addStep(step);
      init();
    }

    @Nullable
    @Override
    protected String getHelpID() {
      return null;
    }

    public AbstractExternalModuleBuilder<GradleProjectSettings> getBuilder() {
      return myBuilder;
    }
  }

  @NotNull
  public static GradleProjectSettings createDefaultProjectSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    settings.setStoreProjectFilesExternally(ThreeState.YES);
    settings.setUseQualifiedModuleNames(true);
    return settings;
  }

  private static void createProjectPreview(Project project, String rootProjectPath, VirtualFile virtualFile) {
    ExternalSystemUtil.refreshProject(project, GradleConstants.SYSTEM_ID, rootProjectPath, true, ProgressExecutionMode.MODAL_SYNC);
    ExternalProjectsManagerImpl.getInstance(project).runWhenInitialized(() -> DumbService.getInstance(project).runWhenSmart(() -> {
      ExternalSystemUtil.ensureToolWindowInitialized(project, GradleConstants.SYSTEM_ID);
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      if (psiFile != null) {
        ProjectView.getInstance(project).selectPsiElement(psiFile, false);
      }
    }));
  }
}
