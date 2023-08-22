// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface RemoteMappingsListener {
  Topic<RemoteMappingsListener> REMOTE_MAPPINGS_CHANGED = new Topic<>(RemoteMappingsListener.class, Topic.BroadcastDirection.NONE);

  void mappingsChanged(@NotNull String prefix, @NotNull String serverId);
  void mappingsChanged();
}
