// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class PluginPathManager {
  private PluginPathManager() {
  }

  private static class SubrepoHolder {
    @NonNls private static final List<String> ROOT_NAMES =
      List.of(
        "android",
        "community",
        "community/android",
        "contrib",
        "CIDR",
        "../ultimate",
        "../ultimate/community",
        "../ultimate/community/android",
        "../ultimate/contrib",
        "../ultimate/CIDR");
    private static final List<File> subrepos = findSubrepos();

    private static List<File> findSubrepos() {
      List<File> result = new ArrayList<>();
      File[] gitRoots = getSortedSubreposRoots(new File(PathManager.getHomePath()));
      for (File subdir : gitRoots) {
        //noinspection IdentifierGrammar
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
      ArrayList<File> result = new ArrayList<>();
      for (String root : ROOT_NAMES) {
        var subRepo = new File(dir, root);
        if (subRepo.exists() && subRepo.isDirectory()) {
          result.add(subRepo.toPath().normalize().toFile());
        }
      }
      File[] gitRoots = result.toArray(new File[0]);

      Arrays.sort(gitRoots, FileUtil::compareFiles);
      return gitRoots;
    }
  }

  public static File getPluginHome(@NonNls String pluginName) {
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

  @Nullable
  public static File getPluginResource(@NotNull Class<?> pluginClass, @NotNull String resourceName) {
    try {
      String jarPath = PathUtil.getJarPathForClass(pluginClass);
      if (!jarPath.endsWith(".jar")) {
        URL resource = pluginClass.getClassLoader().getResource(resourceName);
        if (resource == null) return null;

        return new File(URLUtil.decode(resource.getPath()));
      }
      File jarFile = new File(jarPath);
      if (!jarFile.isFile()) return null;

      File pluginBaseDir = jarFile.getParentFile().getParentFile();
      return new File(pluginBaseDir, resourceName);
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

}
