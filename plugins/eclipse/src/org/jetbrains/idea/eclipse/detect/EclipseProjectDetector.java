// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.detect;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileSystemUtil;
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
import java.util.*;
import java.util.function.Consumer;

class EclipseProjectDetector extends ProjectDetector {
  private final static Logger LOG = Logger.getInstance(EclipseProjectDetector.class);

  protected void collectProjectPaths(List<String> projects) {
    String home = System.getProperty("user.home");
    Path path = Path.of(home, ".eclipse/org.eclipse.oomph.setup/setups/locations.setup");
    File file = path.toFile();
    if (file.exists()) {
      try {
        List<String> workspaceUrls = parseOomphLocations(FileUtil.loadFile(file));
        for (String url : workspaceUrls) {
          scanForProjects(URI.create(url).getPath(), projects);
        }
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }
    for (String appLocation : getStandardAppLocations()) {
      collectProjects(projects, Path.of(appLocation));
    }
    if (PropertiesComponent.getInstance().getBoolean("eclipse.scan.home.directory", true)) {
      visitFiles(new File(home), file1 -> scanForProjects(file1.getPath(), projects), 2);
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
        RecentProjectsManagerBase manager = (RecentProjectsManagerBase)RecentProjectsManager.getInstance();
        @Nls String groupName = EclipseBundle.message("eclipse.projects");
        ProjectGroup group = ContainerUtil.find(manager.getGroups(), g -> groupName.equals(g.getName()));
        String property = "eclipse.projects.detected";
        if (group == null && PropertiesComponent.getInstance().isValueSet(property)) {
          // the group was removed by user
          return;
        }

        List<String> projects = new ArrayList<>();
        new EclipseProjectDetector().collectProjectPaths(projects);
        PropertiesComponent.getInstance().setValue(property, "");
        projects.removeAll(manager.getRecentPaths());
        if (projects.isEmpty()) return;
        HashSet<String> set = new HashSet<>(projects);
        if (group == null) {
          group = new ProjectGroup(groupName);
          group.setBottomGroup(true);
          group.setProjects(new ArrayList<>(set));
          manager.addGroup(group);
        }
        else {
          group.getProjects().retainAll(set);
        }
        ProjectGroup finalGroup = group;
        ApplicationManager.getApplication().invokeLater(() -> onFinish.accept(finalGroup.getProjects()));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  static void collectProjects(List<String> projects, Path path) {
    File file = path.toFile();
    if (!file.exists()) return;
    try {
      String prefs = FileUtil.loadFile(file);
      String[] workspaces = getWorkspaces(prefs);
      for (String workspace : workspaces) {
        scanForProjects(workspace, projects);
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  static String[] getWorkspaces(String prefs) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(prefs));
    String workspaces = properties.getProperty("RECENT_WORKSPACES");
    return workspaces == null ? ArrayUtil.EMPTY_STRING_ARRAY : workspaces.split("\\n");
  }

  static void scanForProjects(String workspace, List<String> projects) {
    if (isInSpecialMacFolder(workspace)) {
      return;
    }
    File[] files = new File(workspace).listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (isInSpecialMacFolder(file.getPath())) {
        continue;
      }
      String[] list = file.list();
      if (list != null && ContainerUtil.or(list, s -> ".project".equals(s)) && ContainerUtil.or(list, s -> ".classpath".equals(s))) {
        projects.add(file.getPath());
      }
    }
  }

  static List<String> parseOomphLocations(String fileContent) throws Exception {
    Element root = JDOMUtil.load(fileContent);
    List<Element> elements = root.getChildren("workspace");
    return ContainerUtil.map(elements, element1 -> StringUtil
      .trimEnd(Objects.requireNonNull(Objects.requireNonNull(element1.getChild("key")).getAttributeValue("href")),
               "/.metadata/.plugins/org.eclipse.oomph.setup/workspace.setup#/"));
  }

  private static boolean isInSpecialMacFolder(String file) {
    if (!SystemInfo.isMac) return false;
    if (FileSystemUtil.isSymLink(file)) {
      return true;
    }
    String home = System.getProperty("user.home");
    Path path = Path.of(file);
    return path.startsWith(Path.of(home, "Documents")) ||
           path.startsWith(Path.of(home, "Pictures")) ||
           path.startsWith(Path.of(home, "Downloads")) ||
           path.startsWith(Path.of(home, "Desktop")) ||
           path.startsWith(Path.of(home, "Library"));
  }

  private static void visitFiles(File file, Consumer<File> processor, int depth) {
    if (depth == 0 || isInSpecialMacFolder(file.getPath())) return;
    processor.accept(file);
    File[] files = file.listFiles(pathname -> !pathname.getName().startsWith("."));
    if (files == null) return;
    for (File child : files) {
      visitFiles( child, processor, depth - 1);
    }
  }
}