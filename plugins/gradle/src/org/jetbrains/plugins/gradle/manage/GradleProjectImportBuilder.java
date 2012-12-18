package org.jetbrains.plugins.gradle.manage;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.util.ui.UIUtil;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleConfigurable;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.gradle.GradleProject;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.task.GradleResolveProjectTask;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * GoF builder for gradle-backed projects.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:29 PM
 */
@SuppressWarnings("MethodMayBeStatic")
public class GradleProjectImportBuilder extends ProjectImportBuilder<GradleProject> {

  @NotNull private final GradleModuleManager     myModuleManager;
  @NotNull private final GradleLibraryManager    myLibraryManager;
  @NotNull private final GradleDependencyManager myDependencyManager;

  private GradleProject      myGradleProject;
  private GradleConfigurable myConfigurable;

  public GradleProjectImportBuilder(@NotNull GradleModuleManager moduleManager,
                                    @NotNull GradleLibraryManager libraryManager,
                                    @NotNull GradleDependencyManager manager)
  {
    myModuleManager = moduleManager;
    myLibraryManager = libraryManager;
    myDependencyManager = manager;
  }

  @NotNull
  @Override
  public String getName() {
    return GradleBundle.message("gradle.name");
  }

  @Override
  public Icon getIcon() {
    return GradleIcons.Gradle;
  }

  @Override
  public List<GradleProject> getList() {
    return Arrays.asList(myGradleProject);
  }

  @Override
  public boolean isMarked(GradleProject element) {
    return true;
  }

  @Override
  public void setList(List<GradleProject> gradleProjects) {
  }

  @Override
  public void setOpenProjectSettingsAfter(boolean on) {
  }

  public GradleConfigurable getConfigurable() {
    return myConfigurable;
  }

  public void prepare(@NotNull WizardContext context) {
    if (myConfigurable == null) {
      myConfigurable = new GradleConfigurable(getProject(context));
      myConfigurable.setAlwaysShowLinkedProjectControls(true);
    }
    myConfigurable.reset();
    String pathToUse = context.getProjectFileDirectory();
    if (!pathToUse.endsWith(GradleConstants.DEFAULT_SCRIPT_NAME)) {
      pathToUse = new File(pathToUse, GradleConstants.DEFAULT_SCRIPT_NAME).getAbsolutePath();
    }
    myConfigurable.setLinkedGradleProjectPath(pathToUse);
  }

  @Override
  public List<Module> commit(final Project project,
                             ModifiableModuleModel model,
                             ModulesProvider modulesProvider,
                             ModifiableArtifactModel artifactModel)
  {
    System.setProperty(GradleConstants.NEWLY_IMPORTED_PROJECT, Boolean.TRUE.toString());
    final GradleProject gradleProject = getGradleProject();
    if (gradleProject != null) {
      final LanguageLevel gradleLanguageLevel = gradleProject.getLanguageLevel();
      final LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
      if (gradleLanguageLevel != languageLevelExtension.getLanguageLevel()) {
        languageLevelExtension.setLanguageLevel(gradleLanguageLevel);
      }
    }
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        GradleSettings.applyLinkedProjectPath(myConfigurable.getLinkedProjectPath(), project);
        GradleSettings.applyPreferLocalInstallationToWrapper(myConfigurable.isPreferLocalInstallationToWrapper(), project);
        GradleSettings.applyGradleHome(myConfigurable.getGradleHomePath(), project);
        // Reset gradle home for default project.
        GradleSettings.applyLinkedProjectPath(null, ProjectManager.getInstance().getDefaultProject());

        if (gradleProject != null) {
          GradleUtil.executeProjectChangeAction(project, project, new Runnable() {
            @Override
            public void run() {
              ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
                @Override
                public void run() {
                  myModuleManager.importModules(gradleProject.getModules(), project, true);
                }
              }); 
            }
          });

          final String linkedProjectPath = myConfigurable.getLinkedProjectPath();
          assert linkedProjectPath != null;
          myConfigurable = null;
          myGradleProject = null;
          
          final Runnable resolveDependenciesTask = new Runnable() {
            @Override
            public void run() {
              ProgressManager.getInstance().run(
                new Task.Backgroundable(project, GradleBundle.message("gradle.library.resolve.progress.text"), false) {
                  @Override
                  public void run(@NotNull final ProgressIndicator indicator) {
                    GradleResolveProjectTask task = new GradleResolveProjectTask(project, linkedProjectPath, true);
                    task.execute(indicator);
                    GradleProject projectWithResolvedLibraries = task.getGradleProject();
                    if (projectWithResolvedLibraries == null) {
                      return;
                    }

                    setupLibraries(projectWithResolvedLibraries, project);
                  }
                });
            }
          };
          UIUtil.invokeLaterIfNeeded(resolveDependenciesTask);
        }
      }
    });
    return Collections.emptyList();
  }

  /**
   * The whole import sequence looks like below:
   * <p/>
   * <pre>
   * <ol>
   *   <li>Get project view from the gradle tooling api without resolving dependencies (downloading libraries);</li>
   *   <li>Allow to adjust project settings before importing;</li>
   *   <li>Create IJ project and modules;</li>
   *   <li>Ask gradle tooling api to resolve library dependencies (download the if necessary);</li>
   *   <li>Configure libraries used by the gradle project at intellij;</li>
   *   <li>Configure library dependencies;</li>
   * </ol>
   * </pre>
   * <p/>
   *
   * @param projectWithResolvedLibraries  gradle project with resolved libraries (libraries have already been downloaded and
   *                                      are available at file system under gradle service directory)
   * @param project                       current intellij project which should be configured by libraries and module library
   *                                      dependencies information available at the given gradle project
   */
  private void setupLibraries(final GradleProject projectWithResolvedLibraries, final Project project) {
    final Set<? extends GradleLibrary> libraries = projectWithResolvedLibraries.getLibraries();
    GradleUtil.executeProjectChangeAction(project, libraries, new Runnable() {
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(new Runnable() {
          @Override
          public void run() {
            // Clean existing libraries (if any).
            LibraryTable projectLibraryTable = ProjectLibraryTable.getInstance(project);
            if (projectLibraryTable == null) {
              GradleLog.LOG.warn(
                "Can't resolve external dependencies of the target gradle project (" + project + "). Reason: project "
                + "library table is undefined"
              );
              return;
            }
            LibraryTable.ModifiableModel model = projectLibraryTable.getModifiableModel();
            try {
              for (Library library : model.getLibraries()) {
                model.removeLibrary(library);
              }
            }
            finally {
              model.commit();
            }

            // Register libraries.
            myLibraryManager.importLibraries(projectWithResolvedLibraries.getLibraries(), project);
            GradleProjectStructureHelper helper = project.getComponent(GradleProjectStructureHelper.class);
            for (GradleModule module : projectWithResolvedLibraries.getModules()) {
              Module intellijModule = helper.findIntellijModule(module);
              assert intellijModule != null;
              myDependencyManager.importDependencies(module.getDependencies(), intellijModule);
            }
          }
        });
      }
    });
  }

  @Nullable
  private File getProjectFile() {
    String path = myConfigurable.getLinkedProjectPath();
    return path == null ? null : new File(path);
  }

  /**
   * Is called to indicate that user changed path to the gradle project to import.
   * 
   * @param path      new directory path
   */
  public void setCurrentProjectPath(@NotNull String path) {
    myConfigurable.setLinkedGradleProjectPath(path);
  }

  /**
   * Asks current builder to ensure that target gradle project is defined.
   *
   * @param wizardContext             current wizard context
   * @throws ConfigurationException   if gradle project is not defined and can't be constructed
   */
  public void ensureProjectIsDefined(@NotNull WizardContext wizardContext) throws ConfigurationException {
    File projectFile = getProjectFile();
    if (projectFile == null) {
      throw new ConfigurationException(GradleBundle.message("gradle.import.text.error.project.undefined"));
    }
    if (projectFile.isDirectory()) {
      throw new ConfigurationException(GradleBundle.message("gradle.import.text.error.directory.instead.file"));
    }
    final Ref<String> errorReason = new Ref<String>();
    final Ref<String> errorDetails = new Ref<String>();
    try {
      final Project project = getProject(wizardContext);
      myGradleProject = GradleUtil.refreshProject(project, projectFile.getAbsolutePath(), errorReason, errorDetails, false, true);
    }
    catch (IllegalArgumentException e) {
      throw new ConfigurationException(e.getMessage(), GradleBundle.message("gradle.import.text.error.cannot.parse.project"));
    }
    if (myGradleProject == null) {
      final String details = errorDetails.get();
      if (!StringUtil.isEmpty(details)) {
        GradleLog.LOG.warn(details);
      }
      String errorMessage;
      String reason = errorReason.get();
      if (reason == null) {
        errorMessage = GradleBundle.message("gradle.import.text.error.resolve.generic.without.reason", projectFile.getPath());
      }
      else {
        errorMessage = GradleBundle.message("gradle.import.text.error.resolve.with.reason", reason);
      }
      throw new ConfigurationException(errorMessage, GradleBundle.message("gradle.import.title.error.resolve.generic"));
    } 
  }

  @Nullable
  public GradleProject getGradleProject() {
    return myGradleProject;
  }

  @Override
  public boolean isSuitableSdkType(SdkTypeId sdk) {
    return sdk == JavaSdk.getInstance();
  }

  /**
   * Applies gradle-plugin-specific settings like project files location etc to the given context.
   * 
   * @param context  storage for the project/module settings.
   */
  public void applyProjectSettings(@NotNull WizardContext context) {
    if (myGradleProject == null) {
      assert false;
      return;
    }
    context.setProjectName(myGradleProject.getName());
    context.setProjectFileDirectory(myGradleProject.getProjectFileDirectoryPath());
    context.setCompilerOutputDirectory(myGradleProject.getCompileOutputPath());
    context.setProjectJdk(myGradleProject.getSdk());
  }

  /**
   * Allows to get {@link Project} instance to use. Basically, there are two alternatives -
   * {@link WizardContext#getProject() project from the current wizard context} and
   * {@link ProjectManager#getDefaultProject() default project}.
   *
   * @param wizardContext   current wizard context
   * @return                {@link Project} instance to use
   */
  @NotNull
  public Project getProject(@NotNull WizardContext wizardContext) {
    Project result = wizardContext.getProject();
    if (result == null) {
      result = ProjectManager.getInstance().getDefaultProject();
    }
    return result;
  }
}
