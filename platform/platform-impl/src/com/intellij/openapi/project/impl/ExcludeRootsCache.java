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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// provides list of all excluded folders across all opened projects, reasonably fast
@SuppressWarnings("SplitModeApiUsage")
final class ExcludeRootsCache {
  private static final class CachedUrls {
    private final long myModificationCount;
    private final String[] myUrls;

    private CachedUrls(long count, String[] urls) {
      myModificationCount = count;
      myUrls = urls;
    }
  }

  private final ConcurrentMap<Project, CachedUrls> myCache = new ConcurrentHashMap<>();

  ExcludeRootsCache(@NotNull SimpleMessageBusConnection connection) {
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        myCache.remove(project);
      }
    });
  }

  @NotNull List<String> getExcludedUrls() {
    return ReadAction.computeBlocking(() -> {
      var projects = ProjectManager.getInstance().getOpenProjects();
      return switch (projects.length) {
        case 0 -> List.of();
        case 1 -> getExcludedUrls(projects[0]);
        default -> {
          var result = new TreeSet<>(OSAgnosticPathUtil.COMPARATOR);
          for (var project : projects) {
            result.addAll(getExcludedUrls(project));
          }
          yield List.of(ArrayUtilRt.toStringArray(result));
        }
      };
    });
  }

  @NotNull List<String> getExcludedUrls(@NotNull Project project) {
    return ReadAction.computeBlocking(() -> {
      @SuppressWarnings("UnsafeOpenServiceCast") var wsm = (WorkspaceModelInternal)WorkspaceModel.getInstance(project);
      var cache = myCache.get(project);
      if (cache != null && cache.myModificationCount == wsm.getEntityStorage().getVersion()) {
        return List.of(cache.myUrls);
      }
      else {
        var result = new TreeSet<>(OSAgnosticPathUtil.COMPARATOR);
        // WSM contributors
        var collector = new ExcludedRootsCollector(result);
        WorkspaceFileIndexImpl.EP_NAME.forEachExtensionSafe(contributor -> {
          switch (contributor.getStorageKind()) {
            case MAIN -> collectExcludedRootsFromContributor(contributor, wsm.getCurrentSnapshot(), collector);
            case UNLOADED -> collectExcludedRootsFromContributor(contributor, wsm.getCurrentSnapshotOfUnloadedEntities(), collector);
          }
        });
        // legacy extensions
        for (var policy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project)) {
          ContainerUtil.addAll(result, policy.getExcludeUrlsForProject());
          for (var module : ModuleManager.getInstance(project).getModules()) {
            @SuppressWarnings("removal")
            var additionalModuleExcludedRoots = policy.getExcludeRootsForModule(ModuleRootManager.getInstance(module));
            result.addAll(ContainerUtil.map(additionalModuleExcludedRoots, VirtualFilePointer::getUrl));
          }
        }
        var urls = ArrayUtilRt.toStringArray(result);
        myCache.put(project, new CachedUrls(wsm.getEntityStorage().getVersion(), urls));
        return List.of(urls);
      }
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
    private final Set<String> excludedUrls;

    private ExcludedRootsCollector(Set<String> excludedUrls) {
      this.excludedUrls = excludedUrls;
    }

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
