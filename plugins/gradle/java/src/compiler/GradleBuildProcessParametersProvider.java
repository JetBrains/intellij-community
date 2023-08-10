// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.compiler;

import com.google.gson.Gson;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import groovy.lang.GroovyObject;
import org.gradle.internal.impldep.com.google.common.base.Optional;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds Gradle build dependencies to the project build process' classpath.
 *
 * @author Vladislav.Soroka
 */
public class GradleBuildProcessParametersProvider extends BuildProcessParametersProvider {

  public static final Logger LOG = Logger.getInstance(GradleBuildProcessParametersProvider.class);
  @NotNull private final Project myProject;

  private List<String> myGradleClasspath;

  public GradleBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public List<String> getClassPath() {
    List<String> result = new ArrayList<>();
    if (!GradleSettings.getInstance(myProject).getLinkedProjectsSettings().isEmpty()) {
      addGradleClassPath(result);
      addOtherClassPath(result);
    }
    return result;
  }

  private void addGradleClassPath(@NotNull final List<String> classpath) {
    if (myGradleClasspath == null) {
      myGradleClasspath = new ArrayList<>();
      String gradleToolingApiJarPath = PathUtil.getJarPathForClass(ProjectConnection.class);
      if (!StringUtil.isEmpty(gradleToolingApiJarPath)) {
        myGradleClasspath.add(gradleToolingApiJarPath);
      }
      String gradleToolingApiImplDepJarPath = PathUtil.getJarPathForClass(Optional.class);
      if (!StringUtil.isEmpty(gradleToolingApiImplDepJarPath)) {
        myGradleClasspath.add(gradleToolingApiImplDepJarPath);
      }
    }
    classpath.addAll(myGradleClasspath);
  }

  private static void addOtherClassPath(@NotNull final List<String> classpath) {
    classpath.add(locateAntLibraries());
    classpath.add(PathUtil.getJarPathForClass(GroovyObject.class));
    classpath.add(PathUtil.getJarPathForClass(Gson.class));
    classpath.add(PathUtil.getJarPathForClass(org.slf4j.Logger.class));
  }

  @NotNull
  private static String locateAntLibraries() {
    var gradleJar = PathManager.getJarForClass(GradleConstants.class);
    if (gradleJar != null) {
      Path pathToAnt = gradleJar.resolveSibling("ant").resolve("ant.jar");
      if (pathToAnt.toFile().isFile()) {
        return pathToAnt.toString();
      }

      if (Files.isDirectory(gradleJar)) {
        // Code runs from IDEA run configuration (code from .class file in out/ directory)
        try {
          return PathUtil.getJarPathForClass(Class.forName("org.apache.tools.ant.taskdefs.Ant"));
        }
        catch (ClassNotFoundException ignore) {
        }
      }
    }
    LOG.warn("Unable to locate ant.jar for build process classpath");
    return "";
  }
}
