// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockModule;
import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathMacroFilter;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class PathMacroManagerTest {
  private static final String APP_HOME = FileUtil.toSystemIndependentName(PathManager.getHomePath());
  private static final String USER_HOME = StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()), "/");

  private final Disposable rootDisposable = Disposer.newDisposable();

  @Before
  public void setUp() {
    MockApplication app = MockApplication.setUp(rootDisposable);
    app.registerService(PathMacros.class, new PathMacrosImpl(false));

    ExtensionsAreaImpl area = app.getExtensionArea();
    ExtensionPointName<PathMacroFilter> epName = PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME;
    area.registerExtensionPoint(epName, "com.intellij.openapi.application.PathMacroFilter", ExtensionPoint.Kind.INTERFACE, rootDisposable);
  }

  @After
  public void tearDown() {
    Disposer.dispose(rootDisposable);
  }

  private MockProject createProject(String basePath) {
    return new MockProject(null, rootDisposable) {
      @Override
      public @Nullable @SystemIndependent String getBasePath() {
        return basePath;
      }
    };
  }

  private MockModule createModule(@NotNull String basePath) {
    MockProject project = createProject(basePath);
    return new MockModule(project) {
      @Override
      public @NotNull Path getModuleNioFile() {
        return Paths.get(project.getBasePath()).resolve("module/module.iml");
      }

      @Override
      public @SystemIndependent @NotNull String getModuleFilePath() {
        return basePath + "/module/module.iml";
      }
    };
  }

  private static void assertReplacements(ReplacePathToMacroMap map, String replacements) {
    for (String s : replacements.split("\n")) {
      String[] split = s.split(" -> ");
      String path = split[0], replacement = split[1], description = "For " + path;
      assertThat(map.substitute(path, true)).describedAs(description).isEqualTo(replacement);
      assertThat(map.substitute("file:" + path, true)).describedAs(description).isEqualTo("file:" + replacement);
      assertThat(map.substitute("file:/" + path, true)).describedAs(description).isEqualTo("file:/" + replacement);
      assertThat(map.substitute("file://" + path, true)).describedAs(description).isEqualTo("file://" + replacement);
      assertThat(map.substitute("jar:" + path, true)).describedAs(description).isEqualTo("jar:" + replacement);
      assertThat(map.substitute("jar:/" + path, true)).describedAs(description).isEqualTo("jar:/" + replacement);
      assertThat(map.substitute("jar://" + path, true)).describedAs(description).isEqualTo("jar://" + replacement);
    }
  }

  @Test
  public void testRightMacrosOrder_RelativeValues_NoVariables() {
    assertReplacements(
      new ModulePathMacroManager(createModule("/tmp/foo")).getReplacePathMap(),
      APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
      USER_HOME + " -> $USER_HOME$\n" +
      "/tmp/foo/module -> $MODULE_DIR$\n" +
      "/tmp/foo -> $MODULE_DIR$/..\n" +
      "/tmp -> $MODULE_DIR$/../..");
  }

  @Test
  public void testPathsOutsideProject() {
    MockProject project = createProject("/tmp/foo");
    assertReplacements(
      new ProjectPathMacroManager(project).getReplacePathMap(),
      APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
      USER_HOME + " -> $USER_HOME$\n" +
      "/tmp/foo -> $PROJECT_DIR$\n" +
      "/tmp -> $PROJECT_DIR$/..");
  }

  @Test
  @SuppressWarnings("SpellCheckingInspection")
  public void testProjectUnderUserHome_ReplaceRecursively() {
    ReplacePathToMacroMap map = new ProjectPathMacroManager(createProject("/home/user/foo")).getReplacePathMap();
    String src = "-Dfoo=/home/user/foo/bar/home -Dbar=\"/home/user\"";
    String dst = "-Dfoo=$PROJECT_DIR$/bar/home -Dbar=\"$PROJECT_DIR$/..\"";
    assertThat(map.substituteRecursively(src, true).toString()).isEqualTo(dst);
  }

  @Test
  public void testProjectUnderUserHome() {
    MockModule module = createModule(USER_HOME + "/IdeaProjects/foo");
    assertReplacements(
      new ModulePathMacroManager(module).getReplacePathMap(),
      APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
      USER_HOME + "/IdeaProjects/foo/module -> $MODULE_DIR$\n" +
      USER_HOME + "/IdeaProjects/foo -> $MODULE_DIR$/..\n" +
      USER_HOME + "/IdeaProjects -> $MODULE_DIR$/../..\n" +
      USER_HOME + " -> $USER_HOME$");
  }

  @Test
  public void testProjectUnderWSL() {
    String wslHome = "//wsl$/Linux";
    MockModule module = createModule(wslHome + "/project");
    assertReplacements(
      new ModulePathMacroManager(module).getReplacePathMap(),
      APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
      USER_HOME + " -> $USER_HOME$\n" +
      wslHome + "/project/module -> $MODULE_DIR$\n" +
      wslHome + "/project -> $MODULE_DIR$/..\n" +
      wslHome + " -> $MODULE_DIR$/../..");
  }
}