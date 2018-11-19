// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.ProjectGroupActionGroup;
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

import static com.intellij.openapi.project.impl.ProjectOpeningTest.closeProject;

/**
 * @author Konstantin Bulenkov
 */
public class RecentProjectsTest extends PlatformTestCase {
  public void testMostRecentOnTop() throws Exception {
    String p1 = createAndOpenProject("p1");
    String p2 = createAndOpenProject("p2");
    String p3 = createAndOpenProject("p3");

    checkRecents("p3", "p2", "p1");

    doReopenCloseAndCheck(p2, "p2", "p3", "p1");
    doReopenCloseAndCheck(p1, "p1", "p2", "p3");
    doReopenCloseAndCheck(p3, "p3", "p1", "p2");
  }

  public void testGroupsOrder() throws Exception {
    String p1 = createAndOpenProject("p1");
    String p2 = createAndOpenProject("p2");
    String p3 = createAndOpenProject("p3");
    String p4 = createAndOpenProject("p4");

    RecentProjectsManager mgr = RecentProjectsManager.getInstance();
    ProjectGroup g1 = new ProjectGroup("g1");
    ProjectGroup g2 = new ProjectGroup("g2");
    mgr.addGroup(g1);
    mgr.addGroup(g2);

    g1.addProject(p1);
    g1.addProject(p2);
    g2.addProject(p3);

    checkGroups("g2", "g1");

    doReopenCloseAndCheckGroups(p4, "g2", "g1");
    doReopenCloseAndCheckGroups(p1, "g1", "g2");
    doReopenCloseAndCheckGroups(p3, "g2", "g1");
  }

  public void testTimestampForOpenProjectUpdatesWhenGetStateCalled() throws Exception {
    Project project = null;
    try {
      File path = createTempDir("z1");
      ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
      project = manager.createProject(null, path.getPath());
      project.save();
      closeProject(project);
      project = manager.loadAndOpenProject(path.getPath());
      long timestamp = getProjectOpenTimestamp("z1");
      Thread.sleep(2);
      Assert.assertTrue("Timestamp for opened project has not been updated", timestamp < getProjectOpenTimestamp("z1"));
    }
    finally {
      closeProject(project);
    }
  }

  private static long getProjectOpenTimestamp(String projectName) {
    List<String> keys = RecentProjectsManagerBase.getInstanceEx().getState()
      .additionalInfo.keySet().stream()
      .filter(s -> s.endsWith(projectName))
      .collect(Collectors.toList());
    return RecentProjectsManagerBase.getInstanceEx().getState().additionalInfo.get(keys.get(0)).projectOpenTimestamp;
  }

  private static void doReopenCloseAndCheck(String projectPath, String... results) throws IOException, JDOMException {
    Project project = ProjectManager.getInstance().loadAndOpenProject(projectPath);
    closeProject(project);
    checkRecents(results);
  }

  private static void doReopenCloseAndCheckGroups(String projectPath, String... results) throws IOException, JDOMException {
    Project project = ProjectManager.getInstance().loadAndOpenProject(projectPath);
    closeProject(project);
    checkGroups(results);
  }

  private static void checkRecents(String... recents) {
    List<String> recentProjects = Arrays.asList(recents);
    RecentProjectsManagerBase.State state = ((RecentProjectsManagerBase)RecentProjectsManager.getInstance()).getState();
    List<String> projects = state.recentPaths.stream()
      .map(s -> new File(s).getName().replace("idea_test_", ""))
      .filter(recentProjects::contains)
      .collect(Collectors.toList());
    Assert.assertEquals(recentProjects, projects);
  }

  private static void checkGroups(String... groups) {
    List<String> recentGroups = Arrays.stream((RecentProjectsManager.getInstance()).getRecentProjectsActions(false, true))
      .filter(a -> a instanceof ProjectGroupActionGroup)
      .map(a -> ((ProjectGroupActionGroup)a).getGroup().getName())
      .collect(Collectors.toList());
    Assert.assertEquals(Arrays.asList(groups), recentGroups);
  }

  private String createAndOpenProject(String name) throws IOException, JDOMException {
    Project project = null;
    try {
      File path = createTempDir(name);
      ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
      project = manager.createProject(null, path.getPath());
      project.save();
      closeProject(project);
      project = manager.loadAndOpenProject(path.getPath());
      return project.getBasePath();
    }
    finally {
      closeProject(project);
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    //this is a service. Initializes lazily
    RecentProjectsManager.getInstance();
  }
}
