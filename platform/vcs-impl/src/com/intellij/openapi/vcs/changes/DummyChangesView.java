/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.commit.*;
import com.intellij.vcs.log.VcsUser;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

class DummyChangesView implements ChangesViewEx {
  DummyChangesView() {
  }

  @Override
  public void scheduleRefresh() {
  }

  @Override
  public void resetViewImmediatelyAndRefreshLater() {
  }

  @Override
  public void selectFile(VirtualFile vFile) {
  }

  @Override
  public void selectChanges(@NotNull List<? extends Change> changes) {
  }

  @Override
  public void updateProgressComponent(@NotNull List<Supplier<JComponent>> progress) {
  }

  @Override
  public void setGrouping(@NotNull String groupingKey) {
  }

  @Override
  public void scheduleRefresh(@NotNull Runnable callback) {
  }

  @Override
  public @NotNull ChangesViewCommitWorkflowUi createCommitPanel() {
    return new DummyChangesViewCommitWorkflowUi();
  }

  @Override
  public @Nullable ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return null;
  }

  private static class DummyChangesViewCommitWorkflowUi implements ChangesViewCommitWorkflowUi {
    @Override
    public boolean isActive() {
      return false;
    }

    @Override
    public void deactivate(boolean isOnCommit) { }

    @Override
    public void endExecution() { }

    @Override
    public @Nullable Object refreshChangesViewBeforeCommit(@NotNull Continuation<? super @NotNull Unit> $completion) {
      return null;
    }

    @Override
    public void setInclusionModel(@Nullable InclusionModel model) { }

    @Override
    public void expand(@NotNull Object item) { }

    @Override
    public void select(@NotNull Object item) { }

    @Override
    public void selectFirst(@NotNull Collection<?> items) { }

    @Override
    public void setCompletionContext(@NotNull List<? extends @NotNull LocalChangeList> changeLists) { }

    @Override
    public @NotNull CommitProgressUi getCommitProgressUi() {
      return null;
    }

    @Override
    public void showCommitOptions(@NotNull CommitOptions options,
                                  @Nls @NotNull String actionName,
                                  boolean isFromToolbar,
                                  @NotNull DataContext dataContext) { }

    @Override
    public boolean isDefaultCommitActionEnabled() {
      return false;
    }

    @Override
    public void setDefaultCommitActionEnabled(boolean b) { }

    @Override
    public void setPrimaryCommitActions(@NotNull List<? extends @NotNull AnAction> actions) { }

    @Override
    public void setCustomCommitActions(@NotNull List<? extends @NotNull AnAction> actions) { }

    @Override
    public @Nullable VcsUser getCommitAuthor() {
      return null;
    }

    @Override
    public void setCommitAuthor(@Nullable VcsUser user) { }

    @Override
    public @Nullable Date getCommitAuthorDate() {
      return null;
    }

    @Override
    public void setCommitAuthorDate(@Nullable Date date) { }

    @Override
    public void addCommitAuthorListener(@NotNull CommitAuthorListener listener, @NotNull Disposable parent) { }

    @Override
    public @NotNull CommitMessageUi getCommitMessageUi() {
      return new CommitMessageUi() {
        @Override
        public @NotNull String getText() {
          return "";
        }

        @Override
        public void setText(@Nullable String text) {
        }

        @Override
        public void focus() {
        }

        @Override
        public void startLoading() {
        }

        @Override
        public void stopLoading() {
        }
      };
    }

    @Override
    public @NlsContexts.Button @NotNull String getDefaultCommitActionName() {
      return "";
    }

    @Override
    public void setDefaultCommitActionName(@NlsContexts.Button @NotNull String s) {
    }

    @Override
    public boolean activate() {
      return false;
    }

    @Override
    public void addDataProvider(@NotNull DataProvider provider) { }

    @Override
    public void addExecutorListener(@NotNull CommitExecutorListener listener, @NotNull Disposable parent) { }

    @Override
    public @NotNull List<@NotNull Change> getDisplayedChanges() {
      return List.of();
    }

    @Override
    public @NotNull List<@NotNull Change> getIncludedChanges() {
      return List.of();
    }

    @Override
    public @NotNull List<@NotNull FilePath> getDisplayedUnversionedFiles() {
      return List.of();
    }

    @Override
    public @NotNull List<@NotNull FilePath> getIncludedUnversionedFiles() {
      return List.of();
    }

    @Override
    public void addInclusionListener(@NotNull InclusionListener listener, @NotNull Disposable parent) { }

    @Override
    public void startBeforeCommitChecks() { }

    @Override
    public void endBeforeCommitChecks(@NotNull CommitChecksResult result) { }

    @Override
    public void dispose() { }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) { }
  }
}
