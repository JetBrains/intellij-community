// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// provides list of all excluded folders across all opened projects, fast.
@SuppressWarnings("SplitModeApiUsage")
final class ExcludeRootsCache {
  private volatile CachedUrls myCache;

  private static final class CachedUrls {
    private final long myModificationCount;
    private final String[] myUrls;

    private CachedUrls(long count, String[] urls) {
      myModificationCount = count;
      myUrls = urls;
    }
  }

  ExcludeRootsCache(@NotNull SimpleMessageBusConnection connection) {
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      @SuppressWarnings("removal")
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
    return ReadAction.computeBlocking(() -> {
      var cache = myCache;
      var actualModCount = Stream.of(ProjectManager.getInstance().getOpenProjects())
        .mapToLong(p -> WorkspaceModel.getInstance(p) instanceof WorkspaceModelInternal wmi ? wmi.getEntityStorage().getVersion() : 0)
        .sum();
      String[] urls;
      if (cache != null && actualModCount == cache.myModificationCount) {
        urls = cache.myUrls;
      }
      else {
        var result = new HashSet<String>();
        for (var project : ProjectManager.getInstance().getOpenProjects()) {
          // WSM contributors
          @SuppressWarnings("UnsafeOpenServiceCast") var workspaceModel = (WorkspaceModelInternal)WorkspaceModel.getInstance(project);
          var currentStorage = workspaceModel.getCurrentSnapshot();
          var unloadedStorage = workspaceModel.getCurrentSnapshotOfUnloadedEntities();
          var collector = new ExcludedRootsCollector();
          for (var contributor : WorkspaceFileIndexImpl.EP_NAME.getExtensionList()) {
            switch (contributor.getStorageKind()) {
              case MAIN -> collectExcludedRootsFromContributor(contributor, currentStorage, collector);
              case UNLOADED -> collectExcludedRootsFromContributor(contributor, unloadedStorage, collector);
            }
          }
          result.addAll(collector.excludedUrls);
          // legacy extensions
          for (var policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
            ContainerUtil.addAll(result, policy.getExcludeUrlsForProject());
            for (var module : ModuleManager.getInstance(project).getModules()) {
              var additionalModuleExcludedRoots = policy.getExcludeRootsForModule(ModuleRootManager.getInstance(module));
              result.addAll(ContainerUtil.map(additionalModuleExcludedRoots, VirtualFilePointer::getUrl));
            }
          }
        }
        urls = ArrayUtilRt.toStringArray(result);
        Arrays.sort(urls, OSAgnosticPathUtil.COMPARATOR);
        myCache = new CachedUrls(actualModCount, urls);
      }
      return List.of(urls);
    });
  }

  private static <E extends WorkspaceEntity> void collectExcludedRootsFromContributor(
    WorkspaceFileIndexContributor<E> contributor,
    EntityStorage storage,
    ExcludedRootsCollector collector
  ) {
    var entities = SequencesKt.asIterable(storage.entities(contributor.getEntityClass()));
    for (var entity : entities) {
      contributor.registerFileSets(entity, collector, storage);
    }
  }

  private static class ExcludedRootsCollector implements WorkspaceFileSetRegistrar {
    private final Set<String> excludedUrls = new HashSet<>();

    @Override
    public void registerFileSet(@NotNull VirtualFileUrl root, @NotNull WorkspaceFileKind kind, @NotNull WorkspaceEntity entity, WorkspaceFileSetData customData) {
      // We only care about excluded roots
    }

    @Override
    @SuppressWarnings("UsagesOfObsoleteApi")
    public void registerFileSet(@NotNull VirtualFile root, @NotNull WorkspaceFileKind kind, @NotNull WorkspaceEntity entity, WorkspaceFileSetData customData) {
      // We only care about excluded roots
    }

    @Override
    public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot, @NotNull WorkspaceEntity entity) {
      excludedUrls.add(excludedRoot.getUrl());
    }

    @Override
    public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot, @NotNull WorkspaceFileKind excludedFrom, @NotNull WorkspaceEntity entity) {
      excludedUrls.add(excludedRoot.getUrl());
    }

    @Override
    public void registerExclusionPatterns(@NotNull VirtualFileUrl root, @NotNull List<String> patterns, @NotNull WorkspaceEntity entity) {
      // Exclusion patterns are not URLs themselves
    }

    @Override
    public void registerExclusionCondition(@NotNull VirtualFileUrl root, @NotNull WorkspaceFileSetExclusionCondition condition, @NotNull WorkspaceEntity entity) {
      // Exclusion conditions are not URLs themselves
    }

    @Override
    public void registerNonRecursiveFileSet(@NotNull VirtualFileUrl file, @NotNull WorkspaceFileKind kind, @NotNull WorkspaceEntity entity, WorkspaceFileSetData customData) {
      // We only care about excluded roots
    }
  }
}
