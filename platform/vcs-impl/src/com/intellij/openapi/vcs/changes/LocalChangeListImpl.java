// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LocalChangeListImpl extends LocalChangeList {
  @NotNull private final Project myProject;
  @NonNls @NotNull private final String myId;
  @NlsSafe @NotNull private final String myName;
  @NlsSafe @NotNull private final String myComment;
  private final @NotNull Set<? extends Change> myChanges;
  @Nullable private final ChangeListData myData;

  private final boolean myIsDefault;
  private final boolean myIsReadOnly;

  @NotNull
  public static LocalChangeListImpl createEmptyChangeListImpl(@NotNull Project project,
                                                              @NlsSafe @NotNull String name,
                                                              @NonNls @Nullable String id) {
    return new Builder(project, name).setId(id).build();
  }

  @NotNull
  public static String generateChangelistId() {
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

  @NotNull
  @Override
  public Set<Change> getChanges() {
    return Collections.unmodifiableSet(myChanges);
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getComment() {
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

  @Nullable
  @Override
  public ChangeListData getData() {
    return myData;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final LocalChangeListImpl list = (LocalChangeListImpl)o;
    return myName.equals(list.myName);
  }

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


  @Override
  public void setComment(@Nullable String comment) {
    ChangeListManager.getInstance(myProject).editComment(myName, comment);
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    ChangeListManager.getInstance(myProject).setReadOnly(myName, isReadOnly);
  }


  public static class Builder {
    @NotNull private final Project myProject;
    @NotNull private final String myName;

    @Nullable private String myId;
    @NotNull private String myComment = "";
    @NotNull private Set<Change> myChanges = new HashSet<>();
    @Nullable private ChangeListData myData = null;
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

    @NotNull
    public LocalChangeListImpl build() {
      String id = myId != null ? myId : generateChangelistId();
      return new LocalChangeListImpl(myProject, id, myName, myComment, myChanges, myData, myIsDefault, myIsReadOnly);
    }
  }
}
