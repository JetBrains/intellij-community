// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class TaskManager {

  public static TaskManager getManager(@NotNull Project project) {
    return project.getService(TaskManager.class);
  }

  public enum VcsOperation {
    CREATE_BRANCH,
    CREATE_CHANGELIST,
    DO_NOTHING
  }

  /**
   * Queries all configured task repositories.
   * Operation may be blocked for a while.
   *
   * @param query text search
   * @return up-to-date issues retrieved from repositories
   * @see #getCachedIssues()
   */
  public abstract List<Task> getIssues(@Nullable String query);

  public abstract List<Task> getIssues(@Nullable String query, boolean forceRequest);

  /**
   * Most arguments have the same meaning as the ones in {@link TaskRepository#getIssues(String, int, int, boolean, ProgressIndicator)}.
   *
   * @param query        optional pattern to filter tasks. One use case is the text entered in "Open Task" dialog.
   * @param offset       first issue, that should be returned by server. It's safe to use 0, if your server doesn't support pagination.
   *                     Or you could calculate it as {@code pageSize * (page - 1)} if it does.
   * @param limit        maximum number of issues returned in one response. You can interpret it as page size.
   * @param withClosed   whether to include closed issues. Downloaded issues will be filtered by {@link Task#isClosed()} anyway, but
   *                     filtering on server side can give more useful results in single request.
   * @param indicator    progress indicator to interrupt long-running requests.
   * @param forceRequest whether to download issues anew or use already cached ones.
   * @return tasks collected from all active repositories
   */
  public abstract List<Task> getIssues(@Nullable String query,
                              int offset,
                              int limit,
                              boolean withClosed,
                              @NotNull ProgressIndicator indicator,
                              boolean forceRequest);

  /**
   * Returns already cached issues.
   *
   * @return cached issues.
   */
  public abstract List<Task> getCachedIssues();

  public abstract List<Task> getCachedIssues(final boolean withClosed);

  public abstract List<LocalTask> getLocalTasks();

  public abstract List<LocalTask> getLocalTasks(final boolean withClosed);

  public abstract LocalTask addTask(Task issue);

  public abstract LocalTask createLocalTask(String summary);

  public abstract LocalTask activateTask(@NotNull Task task, boolean clearContext);

  @NotNull
  public abstract LocalTask getActiveTask();

  @Nullable
  public abstract LocalTask findTask(String id);

  /**
   * Update issue cache asynchronously
   *
   * @param onComplete callback to be invoked after updating
   */
  public abstract void updateIssues(@Nullable Runnable onComplete);

  public abstract boolean isVcsEnabled();

  public abstract AbstractVcs getActiveVcs();

  public abstract boolean isLocallyClosed(LocalTask localTask);

  @Nullable
  public abstract LocalTask getAssociatedTask(LocalChangeList list);

  public abstract void trackContext(LocalChangeList changeList);

  public abstract void removeTask(@NotNull LocalTask task);

  /**
   * @deprecated use {@link TaskManager#addTaskListener(TaskListener, Disposable)}
   */
  @Deprecated(forRemoval = true)
  public abstract void addTaskListener(TaskListener listener);

  public abstract void addTaskListener(@NotNull TaskListener listener, @NotNull Disposable parentDisposable);

  // repositories management

  public abstract TaskRepository[] getAllRepositories();

  public abstract boolean testConnection(TaskRepository repository);
}
