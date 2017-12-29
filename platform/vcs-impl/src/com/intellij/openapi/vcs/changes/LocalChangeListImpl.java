package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * @author yole
 */
public class LocalChangeListImpl extends LocalChangeList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeList");

  @NotNull private final Project myProject;
  @NotNull private final String myId;
  @NotNull private final String myName;
  @NotNull private final String myComment;
  @NotNull private final Set<Change> myChanges;
  @Nullable private final ChangeListData myData;

  private final boolean myIsDefault;
  private final boolean myIsReadOnly;

  @NotNull
  public static LocalChangeListImpl createEmptyChangeListImpl(@NotNull Project project, @NotNull String name, @Nullable String id) {
    return new Builder(project, name).setId(id).build();
  }

  @NotNull
  public static String generateChangelistId() {
    return UUID.randomUUID().toString();
  }

  private LocalChangeListImpl(@NotNull Project project,
                              @NotNull String id,
                              @NotNull String name,
                              @NotNull String comment,
                              @NotNull Set<Change> changes,
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

  private LocalChangeListImpl(@NotNull LocalChangeListImpl origin) {
    myProject = origin.myProject;
    myId = origin.myId;
    myName = origin.myName;
    myComment = origin.myComment;
    myChanges = origin.myChanges;
    myData = origin.myData;
    myIsDefault = origin.myIsDefault;
    myIsReadOnly = origin.myIsReadOnly;
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
    return myName.trim();
  }

  @Override
  public LocalChangeListImpl copy() {
    return this;
  }


  @Override
  public void setName(@NotNull String name) {
    ChangeListManager.getInstance(myProject).editName(myName, name);
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
    @NotNull private Set<Change> myChanges = ContainerUtil.newHashSet();
    @Nullable private ChangeListData myData = null;
    private boolean myIsDefault = false;
    private boolean myIsReadOnly = false;

    public Builder(@NotNull Project project, @NotNull String name) {
      if (StringUtil.isEmptyOrSpaces(name) && Registry.is("vcs.log.empty.change.list.creation")) {
        LOG.info("Creating a changelist with empty name");
      }

      myProject = project;
      myName = name;
    }

    public Builder(@NotNull LocalChangeListImpl list) {
      myProject = list.myProject;
      myId = list.myId;
      myName = list.myName;
      myComment = list.myComment;
      myChanges = list.myChanges;
      myData = list.myData;
      myIsDefault = list.myIsDefault;
      myIsReadOnly = list.myIsReadOnly;
    }

    public Builder setId(@Nullable String value) {
      myId = value;
      return this;
    }

    public Builder setComment(@NotNull String value) {
      myComment = value;
      return this;
    }

    public Builder setChanges(@NotNull Collection<Change> changes) {
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
