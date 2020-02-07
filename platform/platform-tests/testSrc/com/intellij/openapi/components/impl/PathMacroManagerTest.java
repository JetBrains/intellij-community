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

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class PathMacroManagerTest {
  private static final String APP_HOME = FileUtil.toSystemIndependentName(PathManager.getHomePath());
  private static final String USER_HOME = StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()), "/");

  private final Disposable rootDisposable = Disposer.newDisposable();

  @Before
  public void setUp() {
    MockApplication app = MockApplication.setUp(rootDisposable);
    app.registerService(PathMacros.class, new PathMacrosImpl(/* loadContributors = */ false));

    ExtensionsAreaImpl area = app.getExtensionArea();
    ExtensionPointName<PathMacroFilter> epName = PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME;
    area.registerExtensionPoint(epName, "com.intellij.openapi.application.PathMacroFilter", ExtensionPoint.Kind.INTERFACE, rootDisposable);
  }

  @After
  public final void tearDown() {
    Disposer.dispose(rootDisposable);
  }

  @Test
  public void testRightMacrosOrder_RelativeValues_NoVariables() {
    ReplacePathToMacroMap replacePathMap = new ModulePathMacroManager(createModule("/tmp/foo")).getReplacePathMap();
    assertReplacements(replacePathMap, "file:/tmp/foo/module -> file:$MODULE_DIR$\n" +
                                       "file://tmp/foo/module -> file:/$MODULE_DIR$\n" +
                                       "file:///tmp/foo/module -> file://$MODULE_DIR$\n" +
                                       "jar:/tmp/foo/module -> jar:$MODULE_DIR$\n" +
                                       "jar://tmp/foo/module -> jar:/$MODULE_DIR$\n" +
                                       "jar:///tmp/foo/module -> jar://$MODULE_DIR$\n" +
                                       "/tmp/foo/module -> $MODULE_DIR$\n" +
                                       APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
                                       "file:" + APP_HOME + " -> file:$APPLICATION_HOME_DIR$\n" +
                                       "file:/" + APP_HOME + " -> file:/$APPLICATION_HOME_DIR$\n" +
                                       "file://" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                                       "jar:" + APP_HOME + " -> jar:$APPLICATION_HOME_DIR$\n" +
                                       "jar:/" + APP_HOME + " -> jar:/$APPLICATION_HOME_DIR$\n" +
                                       "jar://" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                                       USER_HOME + " -> $USER_HOME$\n" +
                                       "file:" + USER_HOME + " -> file:$USER_HOME$\n" +
                                       "file:/" + USER_HOME + " -> file:/$USER_HOME$\n" +
                                       "file://" + USER_HOME + " -> file://$USER_HOME$\n" +
                                       "jar:" + USER_HOME + " -> jar:$USER_HOME$\n" +
                                       "jar:/" + USER_HOME + " -> jar:/$USER_HOME$\n" +
                                       "jar://" + USER_HOME + " -> jar://$USER_HOME$\n" +
                                       "file:/tmp/foo -> file:$MODULE_DIR$/..\n" +
                                       "file://tmp/foo -> file:/$MODULE_DIR$/..\n" +
                                       "file:///tmp/foo -> file://$MODULE_DIR$/..\n" +
                                       "jar:/tmp/foo -> jar:$MODULE_DIR$/..\n" +
                                       "jar://tmp/foo -> jar:/$MODULE_DIR$/..\n" +
                                       "jar:///tmp/foo -> jar://$MODULE_DIR$/..\n" +
                                       "/tmp/foo -> $MODULE_DIR$/..\n" +
                                       "file:/tmp -> file:$MODULE_DIR$/../..\n" +
                                       "file://tmp -> file:/$MODULE_DIR$/../..\n" +
                                       "file:///tmp -> file://$MODULE_DIR$/../..\n" +
                                       "jar:/tmp -> jar:$MODULE_DIR$/../..\n" +
                                       "jar://tmp -> jar:/$MODULE_DIR$/../..\n" +
                                       "jar:///tmp -> jar://$MODULE_DIR$/../..\n" +
                                       "/tmp -> $MODULE_DIR$/../..");
  }

  @Test
  public void testPathsOutsideProject() {
    MockProject project = createProject("/tmp/foo");
    ReplacePathToMacroMap replacePathMap = new ProjectPathMacroManager(project).getReplacePathMap();
    assertReplacements(replacePathMap, "file:/tmp/foo -> file:$PROJECT_DIR$\n" +
                 "file://tmp/foo -> file:/$PROJECT_DIR$\n" +
                 "file:///tmp/foo -> file://$PROJECT_DIR$\n" +
                 "jar:/tmp/foo -> jar:$PROJECT_DIR$\n" +
                 "jar://tmp/foo -> jar:/$PROJECT_DIR$\n" +
                 "jar:///tmp/foo -> jar://$PROJECT_DIR$\n" +
                 "/tmp/foo -> $PROJECT_DIR$\n" +
                 APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
                 "file:" + APP_HOME + " -> file:$APPLICATION_HOME_DIR$\n" +
                 "file:/" + APP_HOME + " -> file:/$APPLICATION_HOME_DIR$\n" +
                 "file://" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "jar:" + APP_HOME + " -> jar:$APPLICATION_HOME_DIR$\n" +
                 "jar:/" + APP_HOME + " -> jar:/$APPLICATION_HOME_DIR$\n" +
                 "jar://" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 USER_HOME + " -> $USER_HOME$\n" +
                 "file:" + USER_HOME + " -> file:$USER_HOME$\n" +
                 "file:/" + USER_HOME + " -> file:/$USER_HOME$\n" +
                 "file://" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "jar:" + USER_HOME + " -> jar:$USER_HOME$\n" +
                 "jar:/" + USER_HOME + " -> jar:/$USER_HOME$\n" +
                 "jar://" + USER_HOME + " -> jar://$USER_HOME$\n" +
                 "file:/tmp -> file:$PROJECT_DIR$/..\n" +
                 "file://tmp -> file:/$PROJECT_DIR$/..\n" +
                 "file:///tmp -> file://$PROJECT_DIR$/..\n" +
                 "jar:/tmp -> jar:$PROJECT_DIR$/..\n" +
                 "jar://tmp -> jar:/$PROJECT_DIR$/..\n" +
                 "jar:///tmp -> jar://$PROJECT_DIR$/..\n" +
                 "/tmp -> $PROJECT_DIR$/..");
  }

  @NotNull
  private MockProject createProject(@NotNull String basePath) {
    return new MockProject(null, rootDisposable) {
        @Override
        @Nullable
        @SystemIndependent
        public String getBasePath() {
          return basePath;
        }
      };
  }

  private static void assertReplacements(@NotNull ReplacePathToMacroMap map, @NotNull String replacements) {
    for (String s : replacements.split("\n")) {
      String[] split = s.split(" -> ");
      String path = split[0];
      String replaced = split[1];
      assertThat(map.substitute(path, true)).describedAs("For " + path).isEqualTo(replaced);
    }
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testProjectUnderUserHome_ReplaceRecursively() {
    ReplacePathToMacroMap map = new ProjectPathMacroManager(createProject("/home/user/foo")).getReplacePathMap();
    String src = "-Dfoo=/home/user/foo/bar/home -Dbar=\"/home/user\"";
    String dst = "-Dfoo=$PROJECT_DIR$/bar/home -Dbar=\"$PROJECT_DIR$/..\"";
    assertThat(map.substituteRecursively(src, true)).isEqualTo(dst);
  }

  @Test
  public void testProjectUnderUserHome() {
    MockModule module = createModule(USER_HOME + "/IdeaProjects/foo");
    ReplacePathToMacroMap replacePathMap = new ModulePathMacroManager(module).getReplacePathMap();
    assertReplacements(replacePathMap, "file:" + USER_HOME + "/IdeaProjects/foo/module -> file:$MODULE_DIR$\n" +
                 "file:/" + USER_HOME + "/IdeaProjects/foo/module -> file:/$MODULE_DIR$\n" +
                 "file://" + USER_HOME + "/IdeaProjects/foo/module -> file://$MODULE_DIR$\n" +
                 "jar:" + USER_HOME + "/IdeaProjects/foo/module -> jar:$MODULE_DIR$\n" +
                 "jar:/" + USER_HOME + "/IdeaProjects/foo/module -> jar:/$MODULE_DIR$\n" +
                 "jar://" + USER_HOME + "/IdeaProjects/foo/module -> jar://$MODULE_DIR$\n" +
                 USER_HOME + "/IdeaProjects/foo/module -> $MODULE_DIR$\n" +
                 APP_HOME + " -> $APPLICATION_HOME_DIR$\n" +
                 "file:" + APP_HOME + " -> file:$APPLICATION_HOME_DIR$\n" +
                 "file:/" + APP_HOME + " -> file:/$APPLICATION_HOME_DIR$\n" +
                 "file://" + APP_HOME + " -> file://$APPLICATION_HOME_DIR$\n" +
                 "jar:" + APP_HOME + " -> jar:$APPLICATION_HOME_DIR$\n" +
                 "jar:/" + APP_HOME + " -> jar:/$APPLICATION_HOME_DIR$\n" +
                 "jar://" + APP_HOME + " -> jar://$APPLICATION_HOME_DIR$\n" +
                 "file:" + USER_HOME + "/IdeaProjects/foo -> file:$MODULE_DIR$/..\n" +
                 "file:/" + USER_HOME + "/IdeaProjects/foo -> file:/$MODULE_DIR$/..\n" +
                 "file://" + USER_HOME + "/IdeaProjects/foo -> file://$MODULE_DIR$/..\n" +
                 "jar:" + USER_HOME + "/IdeaProjects/foo -> jar:$MODULE_DIR$/..\n" +
                 "jar:/" + USER_HOME + "/IdeaProjects/foo -> jar:/$MODULE_DIR$/..\n" +
                 "jar://" + USER_HOME + "/IdeaProjects/foo -> jar://$MODULE_DIR$/..\n" +
                 USER_HOME + "/IdeaProjects/foo -> $MODULE_DIR$/..\n" +
                 "file:" + USER_HOME + "/IdeaProjects -> file:$MODULE_DIR$/../..\n" +
                 "file:/" + USER_HOME + "/IdeaProjects -> file:/$MODULE_DIR$/../..\n" +
                 "file://" + USER_HOME + "/IdeaProjects -> file://$MODULE_DIR$/../..\n" +
                 "jar:" + USER_HOME + "/IdeaProjects -> jar:$MODULE_DIR$/../..\n" +
                 "jar:/" + USER_HOME + "/IdeaProjects -> jar:/$MODULE_DIR$/../..\n" +
                 "jar://" + USER_HOME + "/IdeaProjects -> jar://$MODULE_DIR$/../..\n" +
                 USER_HOME + "/IdeaProjects -> $MODULE_DIR$/../..\n" +
                 USER_HOME + " -> $USER_HOME$\n" +
                 "file:" + USER_HOME + " -> file:$USER_HOME$\n" +
                 "file:/" + USER_HOME + " -> file:/$USER_HOME$\n" +
                 "file://" + USER_HOME + " -> file://$USER_HOME$\n" +
                 "jar:" + USER_HOME + " -> jar:$USER_HOME$\n" +
                 "jar:/" + USER_HOME + " -> jar:/$USER_HOME$\n" +
                 "jar://" + USER_HOME + " -> jar://$USER_HOME$");
  }

  @NotNull
  private MockModule createModule(@NotNull String basePath) {
    MockProject project = createProject(basePath);
    return new MockModule(project) {
      @Override
      public @NotNull String getModuleFilePath() {
        return project.getBasePath() + "/module/module.iml";
      }
    };
  }
}
