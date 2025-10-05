// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LocalChangeListImpl extends LocalChangeList {
  private final @NotNull Project myProject;
  private final @NonNls @NotNull String myId;
  private final @NlsSafe @NotNull String myName;
  private final @NlsSafe @NotNull String myComment;
  private final @NotNull Set<? extends Change> myChanges;
  private final @Nullable ChangeListData myData;

  private final boolean myIsDefault;
  private final boolean myIsReadOnly;

  public static @NotNull LocalChangeListImpl createEmptyChangeListImpl(@NotNull Project project,
                                                                       @NlsSafe @NotNull String name,
                                                                       @NonNls @Nullable String id) {
    return new Builder(project, name).setId(id).build();
  }

  public static @NotNull String generateChangelistId() {
    return UUID.randomUUID().toString();
  }

  private LocalChangeListImpl(@NotNull Project project,
                              @NonNls @NotNull String id,
                              @NlsSafe @NotNull String name,
                              @NlsSafe @NotNull String comment,
                              @NotNull Set<? extends Change> changes,
                              @Nullable ChangeListData data,
                              boolean isDefault,
                              boolean isReadOnly) {
    myProject = project;
    myId = id;
    myName = name;
    myComment = comment;
    myChanges = changes;
    myData = data;
    myIsDefault = isDefault;
    myIsReadOnly = isReadOnly;
  }

  @Override
  public @NotNull Set<Change> getChanges() {
    return Collections.unmodifiableSet(myChanges);
  }

  @Override
  public @NotNull String getId() {
    return myId;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getComment() {
    return myComment;
  }

  @Override
  public boolean isDefault() {
    return myIsDefault;
  }

  @Override
  public boolean isReadOnly() {
    return myIsReadOnly;
  }

  @Override
  public @Nullable ChangeListData getData() {
    return myData;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final LocalChangeListImpl list = (LocalChangeListImpl)o;
    return myName.equals(list.myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public LocalChangeListImpl copy() {
    return this;
  }

  public static class Builder {
    private final @NotNull Project myProject;
    private final @NotNull String myName;

    private @Nullable String myId;
    private @NotNull String myComment = "";
    private @NotNull Set<Change> myChanges = new HashSet<>();
    private @Nullable ChangeListData myData = null;
    private boolean myIsDefault = false;
    private boolean myIsReadOnly = false;

    public Builder(@NotNull Project project, @NlsSafe @NotNull String name) {
      myProject = project;
      myName = name;
    }

    public Builder(@NotNull LocalChangeListImpl list) {
      myProject = list.myProject;
      myId = list.myId;
      myName = list.myName;
      myComment = list.myComment;
      myChanges = new HashSet<>(list.myChanges);
      myData = list.myData;
      myIsDefault = list.myIsDefault;
      myIsReadOnly = list.myIsReadOnly;
    }

    public Builder setId(@NonNls @Nullable String value) {
      myId = value;
      return this;
    }

    public Builder setComment(@NlsSafe @NotNull String value) {
      myComment = value;
      return this;
    }

    public Builder setChanges(@NotNull Collection<? extends Change> changes) {
      myChanges.clear();
      myChanges.addAll(changes);
      return this;
    }

    public Builder setChangesCollection(@NotNull Set<Change> changes) {
      myChanges = changes;
      return this;
    }

    public Builder setData(@Nullable ChangeListData value) {
      myData = value;
      return this;
    }

    public Builder setDefault(boolean value) {
      myIsDefault = value;
      return this;
    }

    public Builder setReadOnly(boolean value) {
      myIsReadOnly = value;
      return this;
    }

    public @NotNull LocalChangeListImpl build() {
      String id = myId != null ? myId : generateChangelistId();
      return new LocalChangeListImpl(myProject, id, myName, myComment, myChanges, myData, myIsDefault, myIsReadOnly);
    }
  }
}
