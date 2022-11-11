// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Experimental
public interface AdditionalLibraryRootsListener {
  /**
   * This topic is not supposed to be synced directly.
   * {@link #fireAdditionalLibraryChanged(Project, String, Collection, Collection, String)} should be used instead.
   */
  @Topic.ProjectLevel
  Topic<AdditionalLibraryRootsListener> TOPIC = new Topic<>(AdditionalLibraryRootsListener.class, Topic.BroadcastDirection.NONE);

  void libraryRootsChanged(@Nullable @Nls String presentableLibraryName,
                           @NotNull Collection<? extends VirtualFile> oldRoots,
                           @NotNull Collection<? extends VirtualFile> newRoots,
                           @NotNull String libraryNameForDebug);

  /**
   * Use {@code fireAdditionalLibraryChanged()} to notify platform about changes in roots
   * provided by a {@link SyntheticLibrary} from an {@link AdditionalLibraryRootsProvider}.
   * In particular {@code newRoots} would be indexed, and Project View tree would be refreshed. So in hypothetical case
   * when multiple {@link SyntheticLibrary} from same {@link AdditionalLibraryRootsProvider} were changed,
   * this method should be invoked multiple times, once for each library.
   * Due to some listeners method should be invoked under write lock.
   * <p>
   * This method may also be used in a bit different way, with empty `oldRoots` and some (probably empty) `newRoots` to result in rereading
   * values from instances of {@link AdditionalLibraryRootsProvider} and {@link DirectoryIndexExcludePolicy}. In this case `newRoots`
   * is expected to contain those roots that should be added to indexes.
   *
   * @param presentableLibraryName name of {@link SyntheticLibrary} returned by {@link AdditionalLibraryRootsProvider}, may be omitted.
   *                               Used for UI only: in progress titles of indexing, see AdditionalLibraryIndexableAddedFilesIterator.kt
   * @param oldRoots               roots that were in {@link SyntheticLibrary} before
   * @param newRoots               new roots in {@link SyntheticLibrary}
   * @param libraryNameForDebug    some text, making it possible to identify the one who fired event in indexing logs
   */
  @RequiresWriteLock
  static void fireAdditionalLibraryChanged(@NotNull Project project,
                                           @Nullable @Nls String presentableLibraryName,
                                           @NotNull Collection<? extends VirtualFile> oldRoots,
                                           @NotNull Collection<? extends VirtualFile> newRoots,
                                           @NotNull String libraryNameForDebug) {
    AdditionalLibraryRootsListenerHelper.getInstance()
      .handleAdditionalLibraryRootsChanged(project, presentableLibraryName, oldRoots, newRoots, libraryNameForDebug);
    project.getMessageBus().syncPublisher(TOPIC).libraryRootsChanged(presentableLibraryName, oldRoots, newRoots, libraryNameForDebug);
  }
}
