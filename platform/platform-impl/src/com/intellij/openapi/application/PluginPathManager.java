// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public final class PluginPathManager {
  private PluginPathManager() {
  }

  private static class SubrepoHolder {
    private static final Set<String> ROOT_NAMES = ContainerUtil.newTroveSet("community", "contrib", "android", "CIDR");
    private static final List<File> subrepos = findSubrepos();

    private static List<File> findSubrepos() {
      List<File> result = new ArrayList<>();
      File[] gitRoots = getSortedSubreposRoots(new File(PathManager.getHomePath()));
      for (File subdir : gitRoots) {
        File pluginsDir = new File(subdir, "plugins");
        if (pluginsDir.exists()) {
          result.add(pluginsDir);
        }
        else {
          result.add(subdir);
        }
        result.addAll(Arrays.asList(getSortedSubreposRoots(subdir)));
      }
      return result;
    }

    private static File @NotNull [] getSortedSubreposRoots(@NotNull File dir) {
      File[] gitRoots = dir.listFiles(child -> child.isDirectory() && ROOT_NAMES.contains(child.getName()));
      if (gitRoots == null) {
        return new File[0];
      }
      Arrays.sort(gitRoots, (file, file2) -> FileUtil.compareFiles(file, file2));
      return gitRoots;
    }
  }

  public static File getPluginHome(String pluginName) {
    File subrepo = findSubrepo(pluginName);
    if (subrepo != null) {
      return subrepo;
    }
    return new File(PathManager.getHomePath(), "plugins/" + pluginName);
  }

  private static File findSubrepo(String pluginName) {
    for (File subrepo : SubrepoHolder.subrepos) {
      File candidate = new File(subrepo, pluginName);
      if (candidate.isDirectory()) {
        return candidate;
      }
    }
    return null;
  }

  public static String getPluginHomePath(String pluginName) {
    return getPluginHome(pluginName).getPath();
  }

  public static String getPluginHomePathRelative(String pluginName) {
    File subrepo = findSubrepo(pluginName);
    if (subrepo != null) {
      String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
      return "/" + FileUtil.getRelativePath(homePath, FileUtil.toSystemIndependentName(subrepo.getPath()), '/');
    }
    return "/plugins/" + pluginName;
  }
}
