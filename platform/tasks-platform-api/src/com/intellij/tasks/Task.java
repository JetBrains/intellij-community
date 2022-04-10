// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tasks;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Dmitry Avdeev
 */
public abstract class Task {

  public static final Task[] EMPTY_ARRAY = new Task[0];

  /**
   * Global unique task identifier, e.g. IDEA-00001. It's important that its format is consistent with
   * {@link TaskRepository#extractId(String)}, because otherwise task won't be updated on its activation.
   * Note that this ID is used to find issues and to compare them, so (ideally) it has to be unique.
   * 
   * In some cases task server doesn't offer such global ID (but, for instance, pair (project-name, per-project-id) instead) or it's not
   * what users want to see in UI (e.g. notorious <tt>id</tt> and <tt>iid</tt> in Gitlab). In this case you should generate artificial ID 
   * for internal usage and implement {@link #getPresentableId()}.
   *
   * @return unique global ID as described
   *
   * @see #getPresentableId()
   * @see TaskRepository#extractId(String)
   * @see TaskManager#activateTask(Task, boolean)
   */
  public abstract @NotNull String getId();


  /**
   * @return ID in the form that is suitable for commit messages, dialogs, completion items, etc.
   */
  public @NlsSafe @NotNull String getPresentableId() {
    return getId();
  }

  /**
   * Short task description.
   * @return description
   */
  public abstract @Nls @NotNull String getSummary();

  public abstract @Nls @Nullable String getDescription();

  public abstract Comment @NotNull [] getComments();

  public abstract @NotNull Icon getIcon();

  public abstract @NotNull TaskType getType();

  public abstract @Nullable Date getUpdated();

  public abstract @Nullable Date getCreated();

  public abstract boolean isClosed();

  public @Nullable String getCustomIcon() {
    return null;
  }

  /**
   * @return true if bugtracker issue is associated
   */
  public abstract boolean isIssue();

  public abstract @Nullable String getIssueUrl();

  /**
   * @return null if no issue is associated
   * @see #isIssue()
   */
  public @Nullable TaskRepository getRepository() {
    return null;
  }

  public @Nullable TaskState getState() {
    return null;
  }

  @Override
  public final @NlsSafe String toString() {
    String text;
    if (isIssue()) {
      text = getPresentableId() + ": " + getSummary();
    } else {
      text = getSummary();
    }
    return StringUtil.first(text, 60, true);
  }

  public @NlsContexts.Label String getPresentableName() {
    return toString();
  }

  @Override
  public final boolean equals(Object obj) {
    return obj instanceof Task && ((Task)obj).getId().equals(getId());
  }

  @Override
  public final int hashCode() {
    return getId().hashCode();
  }

  /**
   * <b>Per-project</b> issue identifier. Default behavior is to extract project name from task's ID.
   * If your service doesn't provide issue ID in format <tt>PROJECT-123</tt> be sure to initialize it manually,
   * as it will be used to format commit messages.
   *
   * @return project-wide issue identifier
   *
   * @see #getId()
   * @see TaskRepository#getCommitMessageFormat()
   */
  public @NotNull String getNumber() {
    return extractNumberFromId(getId());
  }

  protected static @NotNull String extractNumberFromId(@NotNull String id) {
    int i = id.lastIndexOf('-');
    return i > 0 ? id.substring(i + 1) : id;
  }

  /**
   * Name of the project task belongs to. Default behavior is to extract project name from task's ID.
   * If your service doesn't provide issue ID in format <tt>PROJECT-123</tt> be sure to initialize it manually,
   * as it will be used to format commit messages.
   *
   * @return name of the project
   *
   * @see #getId()
   * @see TaskRepository#getCommitMessageFormat()
   */
  public @Nullable String getProject() {
    return extractProjectFromId(getId());
  }

  protected static @Nullable String extractProjectFromId(@NotNull String id) {
    int i = id.lastIndexOf('-');
    return i > 0 ? id.substring(0, i) : null;
  }
}
