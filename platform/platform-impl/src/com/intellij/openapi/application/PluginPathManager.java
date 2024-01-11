// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URL;
import java.util.*;


public final class PluginPathManager {
  private PluginPathManager() {
  }

  private static final class SubRepoHolder {
    private static final @NonNls List<String> ROOT_NAMES =
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
    private static final List<File> subRepos = findSubRepos();

    private static List<File> findSubRepos() {
      List<File> result = new ArrayList<>();
      File[] gitRoots = getSortedSubReposRoots(new File(PathManager.getHomePath()));
      for (File subdir : gitRoots) {
        //noinspection IdentifierGrammar
        File pluginsDir = new File(subdir, "plugins");
        if (pluginsDir.exists()) {
          result.add(pluginsDir);
        }
        else {
          result.add(subdir);
        }
        result.addAll(Arrays.asList(getSortedSubReposRoots(subdir)));
      }
      return result;
    }

    private static File @NotNull [] getSortedSubReposRoots(@NotNull File dir) {
      Set<File> result = new HashSet<>();
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
    File subRepo = findSubRepo(pluginName);
    if (subRepo != null) {
      return subRepo;
    }
    return new File(PathManager.getHomePath(), "plugins/" + pluginName);
  }

  private static File findSubRepo(String pluginName) {
    for (File subRepo : SubRepoHolder.subRepos) {
      File candidate = new File(subRepo, pluginName);
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
    File subRepo = findSubRepo(pluginName);
    if (subRepo != null) {
      String homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath());
      return "/" + FileUtil.getRelativePath(homePath, FileUtil.toSystemIndependentName(subRepo.getPath()), '/');
    }
    return "/plugins/" + pluginName;
  }

  public static @Nullable File getPluginResource(@NotNull Class<?> pluginClass, @NotNull String resourceName) {
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
