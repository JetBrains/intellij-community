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
package com.intellij.openapi.project.impl;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import org.jdom.JDOMException;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
public class RecentProjectsTest extends ProjectOpeningTest {
  public void testMostRecentOnTop() throws Exception {
    RecentProjectsManager.getInstance();
    String p1 = createAndOpenProject("p1");
    String p2 = createAndOpenProject("p2");
    String p3 = createAndOpenProject("p3");

    checkRecents("p3", "p2", "p1");

    doReopenCloseAndCheck(p2, "p2", "p3", "p1");
    doReopenCloseAndCheck(p1, "p1", "p2", "p3");
    doReopenCloseAndCheck(p3, "p3", "p1", "p2");
  }

  private static void doReopenCloseAndCheck(String projectPath, String... results) throws IOException, JDOMException {
    Project project = ProjectManager.getInstance().loadAndOpenProject(projectPath);
    closeProject(project);
    checkRecents(results);
  }

  private static void checkRecents(String... recents) {
    List<String> recentProjects = Arrays.asList(recents);
    RecentProjectsManagerBase.State state = ((RecentProjectsManagerBase)RecentProjectsManager.getInstance()).getState();
    List<String> projects = state.recentPaths.stream()
      .map(s -> new File(s).getName().replace("idea_test_", ""))
      .filter(s -> recentProjects.contains(s))
      .collect(Collectors.toList());
    Assert.assertArrayEquals(recents, projects.toArray());
  }

  private static String createAndOpenProject(String name) throws IOException, JDOMException, InterruptedException {
    Project project = null;
    try {
      File path = PlatformTestCase.createTempDir(name);
      ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
      project = manager.createProject(null, path.getPath());
      project.save();
      project = manager.loadAndOpenProject(path.getPath());
      return project.getBasePath();
    }
    finally {
      closeProject(project);
    }
  }
}
