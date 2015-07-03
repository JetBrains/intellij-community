/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.platform.loader.PlatformLoader;
import org.jetbrains.platform.loader.repository.RuntimeModuleId;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

/**
 * Adds Gradle build dependencies to the project build process' classpath.
 *
 * @author Vladislav.Soroka
 * @since 7/22/2014
 */
public class GradleBuildProcessParametersProvider extends BuildProcessParametersProvider {
  @NotNull private final Project myProject;

  private List<String> myClasspath;

  public GradleBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public List<String> getClassPath() {
    if (myClasspath == null) {
      myClasspath = ContainerUtil.newArrayList();
      addGradleClassPath(myClasspath);
      final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      for (Module module : moduleManager.getModules()) {
        if (ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
          addOtherClassPath(myClasspath);
          break;
        }
      }
    }
    return myClasspath;
  }

  private static void addGradleClassPath(@NotNull final List<String> classpath) {
    RuntimeModuleId[] modules = {
      RuntimeModuleId.moduleLibrary("gradle", "Gradle"),
      RuntimeModuleId.moduleLibrary("gradle", "commons-io"),
      RuntimeModuleId.moduleLibrary("gradle", "GradleGuava"),
      RuntimeModuleId.moduleLibrary("gradle", "GradleJnaPosix"),
      RuntimeModuleId.moduleLibrary("gradle", "Jsr305"),
      RuntimeModuleId.module("gradle-jps-plugin")
    };
    for (RuntimeModuleId module : modules) {
      classpath.addAll(PlatformLoader.getInstance().getRepository().getModuleRootPaths(module));
    }
  }

  private static void addOtherClassPath(@NotNull final List<String> classpath) {
    RuntimeModuleId[] modules = {
      RuntimeModuleId.projectLibrary("Ant"),
      RuntimeModuleId.projectLibrary("Groovy"),
      RuntimeModuleId.projectLibrary("gson"),
      RuntimeModuleId.projectLibrary("Slf4j")
    };
    for (RuntimeModuleId module : modules) {
      classpath.addAll(PlatformLoader.getInstance().getRepository().getModuleRootPaths(module));
    }
  }
}
