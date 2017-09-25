/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import org.hamcrest.Description;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author mike
 */
@RunWith(JMock.class)
public class PathMacroManagerTest {
  private static final String APP_HOME = FileUtil.toSystemIndependentName(PathManager.getHomePath());
  private static final String USER_HOME = StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()), "/");

  private Module myModule;
  private ProjectEx myProject;
  private PathMacrosImpl myPathMacros;
  private Mockery context;

  protected ApplicationEx myApplication;
  private Disposable myRootDisposable = Disposer.newDisposable();

  @Before
  public final void setupApplication() {
    context = new JUnit4Mockery();
    context.setImposteriser(ClassImposteriser.INSTANCE);
    myApplication = context.mock(ApplicationEx.class, "application");

    context.checking(new Expectations() {
      {
        allowing(myApplication).isUnitTestMode(); will(returnValue(false));
        allowing(myApplication).getName(); will(returnValue("IDEA"));

        // some tests leave invokeLater()'s after them
        allowing(myApplication).invokeLater(with(any(Runnable.class)), with(any(ModalityState.class)));

        allowing(myApplication).runReadAction(with(any(Runnable.class)));
        will(new Action() {
          @Override
          public void describeTo(final Description description) {
            description.appendText("runs runnable");
          }

          @Override
          @Nullable
          public Object invoke(final Invocation invocation) {
            ((Runnable)invocation.getParameter(0)).run();
            return null;
          }
        });
      }
    });

    final ExtensionsArea area = Extensions.getRootArea();
    final String epName = PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.getName();
    if (!area.hasExtensionPoint(epName)) {
      area.registerExtensionPoint(epName, "com.intellij.openapi.application.PathMacroFilter");
      Disposer.register(myRootDisposable, new Disposable() {
        @Override
        public void dispose() {
          area.unregisterExtensionPoint(epName);
        }
      });
    }
  }

  @After
  public final void restoreFilesystem() {
    Disposer.dispose(myRootDisposable);
  }

  private void setUpMocks(final String projectPath) {
    myModule = context.mock(Module.class);
    myPathMacros = context.mock(PathMacrosImpl.class);
    myProject = context.mock(ProjectEx.class);

    final VirtualFile projectFile = context.mock(VirtualFile.class, "projectFile");

    context.checking(new Expectations() {{
      allowing(myModule).isDisposed(); will(returnValue(false));
      allowing(myProject).isDisposed(); will(returnValue(false));

      allowing(projectFile).getPath(); will(returnValue(projectPath));

      final String moduleFilePath = projectPath + "/module/module.iml";

      allowing(myApplication).getComponent(with(equal(PathMacros.class))); will(returnValue(myPathMacros));
      allowing(myPathMacros).addMacroReplacements(with(any(ReplacePathToMacroMap.class)));

      allowing(myProject).getBaseDir(); will(returnValue(projectFile));
      allowing(myProject).getBasePath();
      will(returnValue(projectPath));
      allowing(myModule).getModuleFilePath(); will(returnValue(moduleFilePath));
      allowing(myModule).getProject(); will(returnValue(myProject));
    }});
  }

  @Test
  public void testRightMacrosOrder_RelativeValues_NoVariables() {
    setUpMocks("/tmp/foo");

    final ReplacePathToMacroMap replacePathMap = new ModulePathMacroManager(myPathMacros, myModule).getReplacePathMap();
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
    setUpMocks("/tmp/foo");

    final ReplacePathToMacroMap replacePathMap = new ProjectPathMacroManager(myPathMacros, myProject).getReplacePathMap();
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

  private static void assertReplacements(ReplacePathToMacroMap map, String replacements) {
    for (String s : replacements.split("\n")) {
      String[] split = s.split(" -> ");
      String path = split[0];
      String replaced = split[1];
      assertEquals("For " + path, replaced, map.substitute(path, true));
    }
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Test
  public void testProjectUnderUserHome_ReplaceRecursively() {
    setUpMocks("/home/user/foo");

    ReplacePathToMacroMap map = new ProjectPathMacroManager(myPathMacros, myProject).getReplacePathMap();
    String src = "-Dfoo=/home/user/foo/bar/home -Dbar=\"/home/user\"";
    String dst = "-Dfoo=$PROJECT_DIR$/bar/home -Dbar=\"$PROJECT_DIR$/..\"";
    assertEquals(dst, map.substituteRecursively(src, true));
  }

  @Test
  public void testProjectUnderUserHome() {
    setUpMocks(USER_HOME + "/IdeaProjects/foo");

    final ReplacePathToMacroMap replacePathMap = new ModulePathMacroManager(myPathMacros, myModule).getReplacePathMap();
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
}
