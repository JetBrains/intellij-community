// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.detect;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.ProjectDetector;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.idea.eclipse.EclipseBundle;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

class EclipseProjectDetector extends ProjectDetector {
  private final static Logger LOG = Logger.getInstance(EclipseProjectDetector.class);

  protected void collectProjectPaths(List<String> projects) throws Exception {
    Path path = Path.of(System.getProperty("user.home"), ".eclipse/org.eclipse.oomph.setup/setups/locations.setup");
    File file = path.toFile();
    if (file.exists()) {
      List<String> workspaceUrls = parseOomphLocations(FileUtil.loadFile(file));
      for (String url : workspaceUrls) {
        projects.addAll(scanForProjects(URI.create(url).getPath()));
      }
      return;
    }
    for (String appLocation : getStandardAppLocations()) {
      collectProjects(projects, Path.of(appLocation));
    }
  }

  protected String[] getStandardAppLocations() {
    if (SystemInfo.isMac) {
      return new String[] { "/Applications/Eclipse.app/Contents/Eclipse/configuration/.settings/org.eclipse.ui.ide.prefs" };
    }
    else if (SystemInfo.isWindows) {
      File[] folders = Path.of(System.getProperty("user.home"), "eclipse").toFile().listFiles();
      if (folders != null) {
        return ContainerUtil.map2Array(folders, String.class, file -> file.getPath());
      }
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void detectProjects(Consumer<List<String>> onFinish) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        List<String> projects = new ArrayList<>();
        new EclipseProjectDetector().collectProjectPaths(projects);
        RecentProjectsManagerBase manager = (RecentProjectsManagerBase)RecentProjectsManager.getInstance();
        projects.removeAll(manager.getRecentPaths());
        if (projects.isEmpty()) return;
        @Nls String groupName = EclipseBundle.message("eclipse.projects");
        ProjectGroup group = ContainerUtil.find(manager.getGroups(), g -> groupName.equals(g.getName()));
        if (group == null) {
          group = new ProjectGroup(groupName);
          group.setBottomGroup(true);
          manager.addGroup(group);
        }
        group.setProjects(projects);
        ApplicationManager.getApplication().invokeLater(() -> onFinish.accept(projects));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  static void collectProjects(List<String> projects, Path path) throws IOException {
    File file = path.toFile();
    if (!file.exists()) return;
    String prefs = FileUtil.loadFile(file);
    String[] workspaces = getWorkspaces(prefs);
    for (String workspace : workspaces) {
      projects.addAll(scanForProjects(workspace));
    }
  }

  static String[] getWorkspaces(String prefs) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(prefs));
    String workspaces = properties.getProperty("RECENT_WORKSPACES");
    return workspaces == null ? ArrayUtil.EMPTY_STRING_ARRAY : workspaces.split("\\n");
  }

  static List<String> scanForProjects(String workspace) {
    List<String> projects = new ArrayList<>();
    File[] files = new File(workspace).listFiles();
    if (files == null) {
      return projects;
    }
    for (File file : files) {
      String[] list = file.list();
      if (list != null && ContainerUtil.or(list, s -> ".project".equals(s)) && ContainerUtil.or(list, s -> ".classpath".equals(s))) {
        projects.add(file.getPath());
      }
    }
    return projects;
  }

  static List<String> parseOomphLocations(String fileContent) throws Exception {
    Element root = JDOMUtil.load(fileContent);
    List<Element> elements = root.getChildren("workspace");
    return ContainerUtil.map(elements, element1 -> StringUtil
      .trimEnd(Objects.requireNonNull(Objects.requireNonNull(element1.getChild("key")).getAttributeValue("href")),
               "/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup#/"));
  }
}