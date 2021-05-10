// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
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
                           @NotNull Collection<VirtualFile> newRoots,
                           @NotNull Collection<VirtualFile> oldRoots);

  static void fireAdditionalLibraryChanged(@NotNull Project project,
                                           @NotNull @Nls String presentableLibraryName,
                                           @NotNull Collection<VirtualFile> newRoots,
                                           @NotNull Collection<VirtualFile> oldRoots) {
    if (new HashSet<>(newRoots).equals(new HashSet<>(oldRoots))) return;
    project.getMessageBus().syncPublisher(TOPIC).libraryRootsChanged(presentableLibraryName, newRoots, oldRoots);
  }
}
