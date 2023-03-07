// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Listener, that can be used by {@link VcsBaseContentProvider} to notify {@link LineStatusTrackerManager} about changes.
 */
public interface VcsBaseContentProviderListener {
  @Topic.ProjectLevel
  Topic<VcsBaseContentProviderListener> TOPIC = new Topic<>(VcsBaseContentProviderListener.class, Topic.BroadcastDirection.NONE);

  void onFileBaseContentChanged(@NotNull VirtualFile file);

  void onEverythingChanged();
}
