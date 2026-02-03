// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks;

import com.intellij.tasks.timeTracking.model.WorkItem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class LocalTask extends Task {

  public abstract void setUpdated(Date date);

  public abstract void setActive(boolean active);

  /**
   * @return true if the task is currently active
   */
  @Attribute("active")
  public abstract boolean isActive();

  public abstract void updateFromIssue(Task issue);

  public boolean isDefault() {
    return false;
  }

  // VCS interface

  public abstract @NotNull List<ChangeListInfo> getChangeLists();

  public abstract void addChangelist(ChangeListInfo info);

  public abstract void removeChangelist(final ChangeListInfo info);

  public abstract @Nullable String getShelfName();

  public abstract void setShelfName(String shelfName);

  /**
   * For serialization only.
   * @return two branches per repository: feature-branch itself and original branch to merge into
   * @see #getBranches(boolean)
   */
  public abstract @NotNull List<BranchInfo> getBranches();

  public @NotNull @Unmodifiable List<BranchInfo> getBranches(final boolean original) {
    return ContainerUtil.filter(getBranches(), info -> info.original == original);
  }

  public abstract void addBranch(BranchInfo info);

  public abstract void removeBranch(final BranchInfo info);

  // time tracking interface

  public abstract long getTotalTimeSpent();

  public abstract boolean isRunning();

  public abstract void setRunning(final boolean running);

  public abstract void setWorkItems(List<WorkItem> workItems);

  public abstract List<WorkItem> getWorkItems();

  public abstract void addWorkItem(WorkItem workItem);

  public abstract Date getLastPost();

  public abstract void setLastPost(Date date);

  public abstract long getTimeSpentFromLastPost();
}
