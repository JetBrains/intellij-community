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
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.build.ExternalSystemBuildManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtilRt;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.remote.GradleJavaHelper;
import org.jetbrains.plugins.gradle.remote.impl.GradleBuildManager;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/10/13 1:19 PM
 */
public class GradleManager
  implements ExternalSystemManager<GradleSettingsListener, GradleSettings, GradleLocalSettings, GradleExecutionSettings>
{

  private static final Logger LOG = Logger.getInstance("#" + GradleManager.class.getName());

  @NotNull private final GradleInstallationManager myInstallationManager;

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

  @Override
  public boolean isReady(@NotNull Project project) {
    return myInstallationManager.getGradleHome(project) != null;
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
  public Function<Project, GradleExecutionSettings> getExecutionSettingsProvider() {
    return new Function<Project, GradleExecutionSettings>() {

      private final GradleJavaHelper myJavaHelper = new GradleJavaHelper();

      @Override
      public GradleExecutionSettings fun(Project project) {
        GradleSettings settings = GradleSettings.getInstance(project);
        File gradleHome = myInstallationManager.getGradleHome(project);
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
        GradleExecutionSettings result = new GradleExecutionSettings(localGradlePath,
                                                                     settings.getServiceDirectoryPath(),
                                                                     settings.isPreferLocalInstallationToWrapper());
        for (GradleProjectResolverExtension extension : RESOLVER_EXTENSIONS.getValue()) {
          result.addResolverExtensionClass(extension.getClass().getName());
        }
        String javaHome = myJavaHelper.getJdkHome(project);
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
  public Class<? extends ExternalSystemBuildManager<GradleExecutionSettings>> getBuildManagerClass() {
    return GradleBuildManager.class;
  }
}
