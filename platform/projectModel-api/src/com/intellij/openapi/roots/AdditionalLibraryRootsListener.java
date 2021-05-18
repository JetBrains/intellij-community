// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

@ApiStatus.Experimental
public interface AdditionalLibraryRootsListener {
  @Topic.ProjectLevel
  Topic<AdditionalLibraryRootsListener> TOPIC = new Topic<>(AdditionalLibraryRootsListener.class, Topic.BroadcastDirection.NONE);

  void libraryRootsChanged(@NotNull @Nls String presentableLibraryName,
                           @NotNull Collection<? extends VirtualFile> newRoots,
                           @NotNull Collection<? extends VirtualFile> oldRoots);

  /**
   * Use {@link #fireAdditionalLibraryChanged(Project, String, Collection, Collection)} to notify platform about changes in roots
   * provided by a {@link SyntheticLibrary} from an {@link AdditionalLibraryRootsProvider}.
   * In particular {@code newRoots} would be indexed, and Project View tree would be refreshed. So in hypothetical case
   * when multiple {@link SyntheticLibrary} from same {@link AdditionalLibraryRootsProvider} were changed,
   * this method should be invoked multiple times, once for each library.
   * Due to some listeners method should be invoked under write lock.
   *
   * @param presentableLibraryName - name of {@link SyntheticLibrary} returned by {@link AdditionalLibraryRootsProvider};
   *                               used for UI only
   * @param newRoots               - new roots in {@link SyntheticLibrary}
   * @param oldRoots               - roots that were in {@link SyntheticLibrary} before
   */
  @RequiresWriteLock
  static void fireAdditionalLibraryChanged(@NotNull Project project,
                                           @NotNull @Nls String presentableLibraryName,
                                           @NotNull Collection<? extends VirtualFile> newRoots,
                                           @NotNull Collection<? extends VirtualFile> oldRoots) {
    if (new HashSet<>(newRoots).equals(new HashSet<>(oldRoots))) return;
    project.getMessageBus().syncPublisher(TOPIC).libraryRootsChanged(presentableLibraryName, newRoots, oldRoots);
  }
}
