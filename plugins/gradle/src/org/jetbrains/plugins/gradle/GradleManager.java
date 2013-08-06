/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemConfigurableAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.project.autoimport.CachingExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.messages.MessageBusConnection;
import icons.GradleIcons;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.remote.GradleJavaHelper;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.project.GradleAutoImportAware;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable;
import org.jetbrains.plugins.gradle.settings.*;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 4/10/13 1:19 PM
 */
public class GradleManager
implements ExternalSystemConfigurableAware, ExternalSystemUiAware, ExternalSystemAutoImportAware, StartupActivity, ExternalSystemManager<
  GradleProjectSettings,
  GradleSettingsListener,
  GradleSettings,
  GradleLocalSettings,
  GradleExecutionSettings>
{

  private static final Logger LOG = Logger.getInstance("#" + GradleManager.class.getName());

  @NotNull private final ExternalSystemAutoImportAware myAutoImportDelegate =
    new CachingExternalSystemAutoImportAware(new GradleAutoImportAware());

  @NotNull
  private final GradleInstallationManager myInstallationManager;

  @NotNull private static final NotNullLazyValue<List<GradleProjectResolverExtension>> RESOLVER_EXTENSIONS =
    new NotNullLazyValue<List<GradleProjectResolverExtension>>() {
      @NotNull
      @Override
      protected List<GradleProjectResolverExtension> compute() {
        List<GradleProjectResolverExtension> result = ContainerUtilRt.newArrayList();
        Collections.addAll(result, GradleProjectResolverExtension.EP_NAME.getExtensions());
        ExternalSystemApiUtil.orderAwareSort(result);
        return result;
      }
    };

  public GradleManager(@NotNull GradleInstallationManager manager) {
    myInstallationManager = manager;
  }

  @NotNull
  @Override
  public ProjectSystemId getSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @NotNull
  @Override
  public Function<Project, GradleSettings> getSettingsProvider() {
    return new Function<Project, GradleSettings>() {
      @Override
      public GradleSettings fun(Project project) {
        return GradleSettings.getInstance(project);
      }
    };
  }

  @NotNull
  @Override
  public Function<Project, GradleLocalSettings> getLocalSettingsProvider() {
    return new Function<Project, GradleLocalSettings>() {
      @Override
      public GradleLocalSettings fun(Project project) {
        return GradleLocalSettings.getInstance(project);
      }
    };
  }

  @NotNull
  @Override
  public Function<Pair<Project, String>, GradleExecutionSettings> getExecutionSettingsProvider() {
    return new Function<Pair<Project, String>, GradleExecutionSettings>() {

      private final GradleJavaHelper myJavaHelper = new GradleJavaHelper();

      @Override
      public GradleExecutionSettings fun(Pair<Project, String> pair) {
        GradleSettings settings = GradleSettings.getInstance(pair.first);
        File gradleHome = myInstallationManager.getGradleHome(pair.first, pair.second);
        String localGradlePath = null;
        if (gradleHome != null) {
          try {
            // Try to resolve symbolic links as there were problems with them at the gradle side.
            localGradlePath = gradleHome.getCanonicalPath();
          }
          catch (IOException e) {
            localGradlePath = gradleHome.getAbsolutePath();
          }
        }

        GradleProjectSettings projectLevelSettings = settings.getLinkedProjectSettings(pair.second);
        boolean useWrapper = projectLevelSettings != null && !projectLevelSettings.isPreferLocalInstallationToWrapper();
        GradleExecutionSettings result = new GradleExecutionSettings(localGradlePath,
                                                                     settings.getServiceDirectoryPath(),
                                                                     useWrapper,
                                                                     settings.getGradleVmOptions());

        for (GradleProjectResolverExtension extension : RESOLVER_EXTENSIONS.getValue()) {
          result.addResolverExtensionClass(extension.getClass().getName());
        }
        String javaHome = myJavaHelper.getJdkHome(pair.first);
        if (!StringUtil.isEmpty(javaHome)) {
          LOG.info("Instructing gradle to use java from " + javaHome);
        }
        result.setJavaHome(javaHome);
        return result;
      }
    };
  }

  @Override
  public void enhanceParameters(@NotNull SimpleJavaParameters parameters) throws ExecutionException {
    PathsList classPath = parameters.getClassPath();

    // Gradle i18n bundle.
    ExternalSystemApiUtil.addBundle(classPath, GradleBundle.PATH_TO_BUNDLE, GradleBundle.class);

    // Gradle tool jars.
    String toolingApiPath = PathManager.getJarPathForClass(ProjectConnection.class);
    if (toolingApiPath == null) {
      LOG.warn(GradleBundle.message("gradle.generic.text.error.jar.not.found"));
      throw new ExecutionException("Can't find gradle libraries");
    }
    File gradleJarsDir = new File(toolingApiPath).getParentFile();
    String[] gradleJars = gradleJarsDir.list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.endsWith(".jar");
      }
    });
    if (gradleJars == null) {
      LOG.warn(GradleBundle.message("gradle.generic.text.error.jar.not.found"));
      throw new ExecutionException("Can't find gradle libraries at " + gradleJarsDir.getAbsolutePath());
    }
    for (String jar : gradleJars) {
      classPath.add(new File(gradleJarsDir, jar).getAbsolutePath());
    }

    List<String> additionalEntries = ContainerUtilRt.newArrayList();
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(JavaProjectData.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(LanguageLevel.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(StdModuleTypes.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(JavaModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(ModuleType.class));
    ContainerUtilRt.addIfNotNull(additionalEntries, PathUtil.getJarPathForClass(EmptyModuleType.class));
    for (String entry : additionalEntries) {
      classPath.add(entry);
    }

    for (GradleProjectResolverExtension extension : RESOLVER_EXTENSIONS.getValue()) {
      extension.enhanceParameters(parameters);
    }
  }

  @NotNull
  @Override
  public Class<? extends ExternalSystemProjectResolver<GradleExecutionSettings>> getProjectResolverClass() {
    return GradleProjectResolver.class;
  }

  @Override
  public Class<? extends ExternalSystemTaskManager<GradleExecutionSettings>> getTaskManagerClass() {
    return GradleTaskManager.class;
  }

  @NotNull
  @Override
  public Configurable getConfigurable(@NotNull Project project) {
    return new GradleConfigurable(project);
  }

  @Nullable
  @Override
  public FileChooserDescriptor getExternalProjectConfigDescriptor() {
    return GradleUtil.getGradleProjectFileChooserDescriptor();
  }

  @Nullable
  @Override
  public Icon getProjectIcon() {
    return GradleIcons.Gradle;
  }

  @Nullable
  @Override
  public Icon getTaskIcon() {
    return DefaultExternalSystemUiAware.INSTANCE.getTaskIcon();
  }

  @NotNull
  @Override
  public String getProjectRepresentationName(@NotNull String targetProjectPath, @Nullable String rootProjectPath) {
    return ExternalSystemApiUtil.getProjectRepresentationName(targetProjectPath, rootProjectPath);
  }

  @Nullable
  @Override
  public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
    return myAutoImportDelegate.getAffectedExternalProjectPath(changedFileOrDirPath, project);
  }

  @NotNull
  @Override
  public FileChooserDescriptor getExternalProjectDescriptor() {
    return GradleUtil.getGradleProjectFileChooserDescriptor();
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    // We want to automatically refresh linked projects on gradle service directory change.
    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleSettings.getInstance(project).getChangesTopic(), new GradleSettingsListenerAdapter() {
      @Override
      public void onServiceDirectoryPathChange(@Nullable String oldPath, @Nullable String newPath) {
        ExternalSystemUtil.refreshProjects(project, GradleConstants.SYSTEM_ID, true);
      }
    });

    // We used to assume that gradle scripts are always named 'build.gradle' and kept path to that build.gradle file at ide settings.
    // However, it was found out that that is incorrect assumption (IDEA-109064). Now we keep paths to gradle script's directories
    // instead. However, we don't want to force old users to re-import gradle projects because of that. That's why we check gradle
    // config and re-point it from build.gradle to the parent dir if necessary.
    Map<String, String> adjustedPaths = patchLinkedProjects(project);
    if (adjustedPaths == null) {
      return;
    }

    GradleLocalSettings localSettings = GradleLocalSettings.getInstance(project);
    patchRecentTasks(adjustedPaths, localSettings);
    patchAvailableProjects(adjustedPaths, localSettings);
    patchAvailableTasks(adjustedPaths, localSettings);
  }

  @Nullable
  private static Map<String, String> patchLinkedProjects(@NotNull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    Collection<GradleProjectSettings> correctedSettings = ContainerUtilRt.newArrayList();
    Map<String/* old path */, String/* new path */> adjustedPaths = ContainerUtilRt.newHashMap();
    for (GradleProjectSettings projectSettings : settings.getLinkedProjectsSettings()) {
      String oldPath = projectSettings.getExternalProjectPath();
      if (!new File(oldPath).isDirectory()) {
        try {
          String newPath = new File(oldPath).getParentFile().getCanonicalPath();
          projectSettings.setExternalProjectPath(newPath);
          adjustedPaths.put(oldPath, newPath);
        }
        catch (IOException e) {
          LOG.warn(String.format(
            "Unexpected exception occurred on attempt to re-point linked gradle project path from build.gradle to its parent dir. Path: %s",
            oldPath
          ), e);
        }
      }
      correctedSettings.add(projectSettings);
    }
    if (adjustedPaths.isEmpty()) {
      return null;
    }

    settings.setLinkedProjectsSettings(correctedSettings);
    return adjustedPaths;
  }

  private static void patchAvailableTasks(@NotNull Map<String, String> adjustedPaths, @NotNull GradleLocalSettings localSettings) {
    Map<String, Collection<ExternalTaskPojo>> adjustedAvailableTasks = ContainerUtilRt.newHashMap();
    for (Map.Entry<String, Collection<ExternalTaskPojo>> entry : localSettings.getAvailableTasks().entrySet()) {
      String newPath = adjustedPaths.get(entry.getKey());
      if (newPath == null) {
        adjustedAvailableTasks.put(entry.getKey(), entry.getValue());
      }
      else {
        for (ExternalTaskPojo task : entry.getValue()) {
          String newTaskPath = adjustedPaths.get(task.getLinkedExternalProjectPath());
          if (newTaskPath != null) {
            task.setLinkedExternalProjectPath(newTaskPath);
          }
        }
        adjustedAvailableTasks.put(newPath, entry.getValue());
      }
    }
    localSettings.setAvailableTasks(adjustedAvailableTasks);
  }

  private static void patchAvailableProjects(@NotNull Map<String, String> adjustedPaths, @NotNull GradleLocalSettings localSettings) {
    Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> adjustedAvailableProjects = ContainerUtilRt.newHashMap();
    for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : localSettings.getAvailableProjects().entrySet()) {
      String newPath = adjustedPaths.get(entry.getKey().getPath());
      if (newPath == null) {
        adjustedAvailableProjects.put(entry.getKey(), entry.getValue());
      }
      else {
        adjustedAvailableProjects.put(new ExternalProjectPojo(entry.getKey().getName(), newPath), entry.getValue());
      }
    }
    localSettings.setAvailableProjects(adjustedAvailableProjects);
  }

  private static void patchRecentTasks(@NotNull Map<String, String> adjustedPaths, @NotNull GradleLocalSettings localSettings) {
    for (ExternalTaskExecutionInfo taskInfo : localSettings.getRecentTasks()) {
      ExternalSystemTaskExecutionSettings s = taskInfo.getSettings();
      String newPath = adjustedPaths.get(s.getExternalProjectPath());
      if (newPath != null) {
        s.setExternalProjectPath(newPath);
      }
    }
  }
}
