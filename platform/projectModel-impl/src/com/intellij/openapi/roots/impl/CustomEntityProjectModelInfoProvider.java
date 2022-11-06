// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * This interface is highly experimental and is used for attempts to design project model for various plugins and IDEs
 * based on Workspace Model only.
 * This particular interface provides information how to treat custom entities in terms of sources/libraries/sdks
 * and {@link com.intellij.openapi.roots.ProjectFileIndex}.
 */
@ApiStatus.Experimental
public interface CustomEntityProjectModelInfoProvider<T extends WorkspaceEntity> {
  ExtensionPointName<CustomEntityProjectModelInfoProvider<?>> EP =
    new ExtensionPointName<>("com.intellij.customEntityProjectModelInfoProvider");

  Class<T> getEntityClass();

  @NotNull
  default Sequence<@NotNull CustomContentRoot<T>> getContentRoots(@NotNull Sequence<T> entities,
                                                                  @NotNull EntityStorage entityStorage) {
    return SequencesKt.emptySequence();
  }

  @NotNull
  default Sequence<@NotNull LibraryRoots<T>> getLibraryRoots(@NotNull Sequence<T> entities,
                                                             @NotNull EntityStorage entityStorage) {
    return SequencesKt.emptySequence();
  }

  @NotNull
  default Sequence<@NotNull ExcludeStrategy<T>> getExcludeSdkRootStrategies(@NotNull Sequence<T> entities,
                                                                            @NotNull EntityStorage entityStorage) {
    return SequencesKt.emptySequence();
  }

  class CustomContentRoot<T> {

    @NotNull
    public final T generativeEntity;

    @NotNull
    public final ModuleEntity parentModule;

    @NotNull
    public final VirtualFile root;

    public CustomContentRoot(@NotNull T generativeEntity,
                             @NotNull ModuleEntity parentModule,
                             @NotNull VirtualFile root) {
      this.generativeEntity = generativeEntity;
      this.parentModule = parentModule;
      this.root = root;
    }
  }

  class LibraryRoots<T> {
    @NotNull
    public final T generativeEntity;
    @NotNull
    public final Collection<VirtualFile> sources;
    @NotNull
    public final Collection<VirtualFile> classes;
    @NotNull
    public final Collection<VirtualFile> excluded;
    @Nullable
    public final SyntheticLibrary.ExcludeFileCondition excludeFileCondition;

    public LibraryRoots(@NotNull T generativeEntity,
                        @NotNull Collection<VirtualFile> sources,
                        @NotNull Collection<VirtualFile> classes,
                        @NotNull Collection<VirtualFile> excluded,
                        @Nullable SyntheticLibrary.ExcludeFileCondition excludeFileCondition) {
      this.generativeEntity = generativeEntity;
      this.sources = List.copyOf(sources);
      this.classes = List.copyOf(classes);
      this.excluded = List.copyOf(excluded);
      this.excludeFileCondition = excludeFileCondition;
    }
  }

  class ExcludeStrategy<T> {
    @NotNull
    public final T generativeEntity;
    /**
     * Supply all file urls (existing as well as not yet created) that should be treated as 'excluded'
     */
    @NotNull
    public final List<@NotNull VirtualFileUrl> excludeUrls;
    @Nullable
    public final Function<Sdk, List<VirtualFile>> excludeSdkRootsStrategy;

    public ExcludeStrategy(@NotNull T generativeEntity,
                           @NotNull List<@NotNull VirtualFileUrl> excludeUrls,
                           @Nullable Function<Sdk, List<VirtualFile>> excludeSdkRootsStrategy) {
      this.generativeEntity = generativeEntity;
      this.excludeUrls = excludeUrls;
      this.excludeSdkRootsStrategy = excludeSdkRootsStrategy;
    }
  }
}
