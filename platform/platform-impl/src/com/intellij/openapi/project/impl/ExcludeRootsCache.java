// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

// provides list of all excluded folders across all opened projects, fast.
final class ExcludeRootsCache {
  private volatile CachedUrls myCache;

  private static class CachedUrls {
    private final long myModificationCount;
    @NotNull
    private final String[] myUrls;

    private CachedUrls(long count, @NotNull String[] urls) {
      myModificationCount = count;
      myUrls = urls;
    }
  }

  ExcludeRootsCache(@NotNull MessageBusConnection connection) {
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        myCache = null;
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        myCache = null;
      }
    });
  }

  @NotNull
  String[] getExcludedUrls() {
    return ReadAction.compute(() -> {
      CachedUrls cache = myCache;
      long actualModCount = Arrays.stream(ProjectManager.getInstance().getOpenProjects())
        .map(ProjectRootManager::getInstance)
        .mapToLong(ProjectRootManager::getModificationCount)
        .sum();
      String[] urls;
      if (cache != null && actualModCount == cache.myModificationCount) {
        urls = cache.myUrls;
      }
      else {
        Collection<String> excludedUrls = new THashSet<>();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          for (Module module : ModuleManager.getInstance(project).getModules()) {
            urls = ModuleRootManager.getInstance(module).getExcludeRootUrls();
            ContainerUtil.addAll(excludedUrls, urls);
          }
        }
        urls = ArrayUtilRt.toStringArray(excludedUrls);
        Arrays.sort(urls);
        myCache = new CachedUrls(actualModCount, urls);
      }
      return urls;
    });
  }
}
