// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// provides list of all excluded folders across all opened projects, fast.
final class ExcludeRootsCache {
  private volatile CachedUrls myCache;

  private final static class CachedUrls {
    private final long myModificationCount;
    private final String @NotNull [] myUrls;

    private CachedUrls(long count, String @NotNull [] urls) {
      myModificationCount = count;
      myUrls = urls;
    }
  }

  ExcludeRootsCache(@NotNull SimpleMessageBusConnection connection) {
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

  @NotNull List<String> getExcludedUrls() {
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
        Collection<String> excludedUrls = new HashSet<>();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          for (Module module : ModuleManager.getInstance(project).getModules()) {
            Collections.addAll(excludedUrls, ModuleRootManager.getInstance(module).getExcludeRootUrls());
          }
        }
        urls = ArrayUtilRt.toStringArray(excludedUrls);
        Arrays.sort(urls);
        myCache = new CachedUrls(actualModCount, urls);
      }
      return Arrays.asList(urls);
    });
  }
}
