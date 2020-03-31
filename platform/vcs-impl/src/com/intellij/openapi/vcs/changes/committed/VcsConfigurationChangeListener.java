// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface VcsConfigurationChangeListener {
  Topic<Notification> BRANCHES_CHANGED = new Topic<>("branch mapping changed", Notification.class);
  Topic<DetailedNotification> BRANCHES_CHANGED_RESPONSE = new Topic<>("branch mapping changed (detailed)", DetailedNotification.class);

  interface Notification {
    void execute(@NotNull Project project, @NotNull VirtualFile vcsRoot);
  }

  interface DetailedNotification {
    void execute(@NotNull Project project, @Nullable VirtualFile vcsRoot, @NotNull List<CommittedChangeList> cachedList);
  }
}
