// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.file.BatchFileChangeListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Stack;

@ApiStatus.Internal
public final class DumbServiceVfsBatchListener {

  public DumbServiceVfsBatchListener(@NotNull Project myProject,
                                     @NotNull MergingQueueGuiSuspender heavyActivities) {
    //noinspection UseOfObsoleteCollectionType
    ApplicationManager.getApplication().getMessageBus().connect(myProject)
      .subscribe(BatchFileChangeListener.TOPIC, new BatchFileChangeListener() {
        // synchronized, can be accessed from different threads
        @SuppressWarnings("UnnecessaryFullyQualifiedName")
        final java.util.Stack<AccessToken> stack = new Stack<>();

        @Override
        public void batchChangeStarted(@NotNull Project project, @Nullable String activityName) {
          if (project == myProject) {
            stack.push(heavyActivities.heavyActivityStarted(activityName != null ? UIUtil.removeMnemonic(activityName) : IdeBundle.message("progress.file.system.changes")));
          }
        }

        @Override
        public void batchChangeCompleted(@NotNull Project project) {
          if (project != myProject) return;

          //noinspection UseOfObsoleteCollectionType
          Stack<AccessToken> tokens = stack;
          if (!tokens.isEmpty()) { // just in case
            tokens.pop().finish();
          }
        }
      });
  }
}
