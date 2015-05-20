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

import com.google.gson.Gson;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import groovy.lang.GroovyObject;
import org.apache.tools.ant.taskdefs.Ant;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.slf4j.Logger;
import org.slf4j.impl.Log4jLoggerFactory;

import java.io.File;
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
    String gradleLibDirPath = null;
    String gradleToolingApiJarPath = PathUtil.getJarPathForClass(ProjectConnection.class);
    if (!StringUtil.isEmpty(gradleToolingApiJarPath)) {
      gradleLibDirPath = PathUtil.getParentPath(gradleToolingApiJarPath);
    }
    if (gradleLibDirPath == null || gradleLibDirPath.isEmpty()) return;

    File gradleLibDir = new File(gradleLibDirPath);
    if (!gradleLibDir.isDirectory()) return;

    File[] children = FileUtil.notNullize(gradleLibDir.listFiles());
    for (File child : children) {
      final String fileName = child.getName();
      if (fileName.endsWith(".jar") && child.isFile()) {
        classpath.add(child.getAbsolutePath());
      }
    }
  }

  private static void addOtherClassPath(@NotNull final List<String> classpath) {
    classpath.add(PathUtil.getJarPathForClass(Ant.class));
    classpath.add(PathUtil.getJarPathForClass(GroovyObject.class));
    classpath.add(PathUtil.getJarPathForClass(Gson.class));
    classpath.add(PathUtil.getJarPathForClass(Logger.class));
    classpath.add(PathUtil.getJarPathForClass(Log4jLoggerFactory.class));
  }
}
