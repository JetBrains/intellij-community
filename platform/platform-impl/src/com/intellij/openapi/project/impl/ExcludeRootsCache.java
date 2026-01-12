// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.messages.SimpleMessageBusConnection;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar;
import kotlin.jvm.functions.Function1;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// provides list of all excluded folders across all opened projects, fast.
final class ExcludeRootsCache {
  private volatile CachedUrls myCache;

  private static final class CachedUrls {
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
        .mapToLong(project -> {
          WorkspaceModelInternal workspaceModel = (WorkspaceModelInternal)WorkspaceModel.getInstance(project);
          return workspaceModel.getEntityStorage().getVersion();
        })
        .sum();
      String[] urls;
      if (cache != null && actualModCount == cache.myModificationCount) {
        urls = cache.myUrls;
      }
      else {
        Set<String> excludedUrls = new HashSet<>();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          WorkspaceModel workspaceModel = WorkspaceModel.getInstance(project);
          EntityStorage storage = workspaceModel.getCurrentSnapshot();

          // Collect excluded URLs from all contributors using the same pattern as ProjectRootManagerComponent
          ExtensionPointName<WorkspaceFileIndexContributor<?>> EP_NAME = ExtensionPointName.create("com.intellij.workspaceModel.fileIndexContributor");
          ExcludedRootsCollector collector = new ExcludedRootsCollector();
          for (WorkspaceFileIndexContributor<?> contributor : EP_NAME.getExtensionList()) {
            collectExcludedRootsFromContributor(contributor, storage, collector);
          }
          excludedUrls.addAll(collector.getExcludedUrls());
        }
        urls = ArrayUtilRt.toStringArray(excludedUrls);
        Arrays.sort(urls);
        myCache = new CachedUrls(actualModCount, urls);
      }
      return Arrays.asList(urls);
    });
  }

  private static <E extends WorkspaceEntity> void collectExcludedRootsFromContributor(
    WorkspaceFileIndexContributor<E> contributor,
    EntityStorage storage,
    ExcludedRootsCollector collector) {
    // Convert Kotlin Sequence to Iterable for Java iteration
    Iterable<E> entities = SequencesKt.asIterable(storage.entities(contributor.getEntityClass()));
    for (E entity : entities) {
      contributor.registerFileSets(entity, collector, storage);
    }
  }

  private static class ExcludedRootsCollector implements WorkspaceFileSetRegistrar {
    private final Set<String> excludedUrls = new HashSet<>();

    @Override
    public void registerFileSet(@NotNull VirtualFileUrl root,
                                @NotNull WorkspaceFileKind kind,
                                @NotNull WorkspaceEntity entity,
                                WorkspaceFileSetData customData) {
      // We only care about excluded roots
    }

    @Override
    public void registerFileSet(@NotNull VirtualFile root,
                                @NotNull WorkspaceFileKind kind,
                                @NotNull WorkspaceEntity entity,
                                WorkspaceFileSetData customData) {
      // We only care about excluded roots
    }

    @Override
    public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot, @NotNull WorkspaceEntity entity) {
      excludedUrls.add(excludedRoot.getUrl());
    }

    @Override
    public void registerExcludedRoot(@NotNull VirtualFileUrl excludedRoot,
                                     @NotNull WorkspaceFileKind excludedFrom,
                                     @NotNull WorkspaceEntity entity) {
      excludedUrls.add(excludedRoot.getUrl());
    }

    @Override
    public void registerExclusionPatterns(@NotNull VirtualFileUrl root,
                                         @NotNull List<String> patterns,
                                         @NotNull WorkspaceEntity entity) {
      // Exclusion patterns are not URLs themselves
    }

    @Override
    public void registerExclusionCondition(@NotNull VirtualFileUrl root,
                                          @NotNull Function1<? super VirtualFile, Boolean> condition,
                                          @NotNull WorkspaceEntity entity) {
      // Exclusion conditions are not URLs themselves
    }

    @Override
    public void registerNonRecursiveFileSet(@NotNull VirtualFileUrl file,
                                           @NotNull WorkspaceFileKind kind,
                                           @NotNull WorkspaceEntity entity,
                                           WorkspaceFileSetData customData) {
      // We only care about excluded roots
    }

    @NotNull
    Set<String> getExcludedUrls() {
      return excludedUrls;
    }
  }
}
