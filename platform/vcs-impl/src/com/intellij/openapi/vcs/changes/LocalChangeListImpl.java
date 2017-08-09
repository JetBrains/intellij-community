package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * @author yole
 */
public class LocalChangeListImpl extends LocalChangeList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeList");

  @NotNull private final Project myProject;
  @NotNull private final Set<Change> myChanges;
  private Set<Change> myReadChangesCache = null;
  @NotNull private final String myId;
  @NotNull private final String myName;
  @NotNull private String myComment = "";
  @Nullable private Object myData;

  private boolean myIsDefault = false;
  private boolean myIsReadOnly = false;

  @NotNull
  public static LocalChangeListImpl createEmptyChangeListImpl(@NotNull Project project, @NotNull String name, @Nullable String id) {
    return new LocalChangeListImpl(project, name, id);
  }

  private LocalChangeListImpl(@NotNull Project project, @NotNull String name, @Nullable String id) {
    myProject = project;
    myId = id != null ? id : UUID.randomUUID().toString();
    myName = validateName(name);

    myChanges = ContainerUtil.newHashSet();
  }

  private LocalChangeListImpl(@NotNull LocalChangeListImpl origin, @NotNull String name) {
    myId = origin.getId();
    myProject = origin.myProject;
    myName = validateName(name);

    myComment = origin.myComment;
    myIsDefault = origin.myIsDefault;
    myIsReadOnly = origin.myIsReadOnly;
    myData = origin.myData;

    myChanges = ContainerUtil.newHashSet(origin.myChanges);
    myReadChangesCache = origin.myReadChangesCache;
  }

  @NotNull
  @Override
  public Set<Change> getChanges() {
    if (myReadChangesCache == null) {
      myReadChangesCache = Collections.unmodifiableSet(ContainerUtil.newHashSet(myChanges));
    }
    return myReadChangesCache;
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
  private static String validateName(@NotNull String name) {
    if (StringUtil.isEmptyOrSpaces(name) && Registry.is("vcs.log.empty.change.list.creation")) {
      LOG.info("Creating a changelist with empty name");
    }
    return name;
  }

  @NotNull
  @Override
  public String getComment() {
    return myComment;
  }

  public void setCommentImpl(@Nullable String comment) {
    myComment = comment != null ? comment : "";
  }

  @Override
  public boolean isDefault() {
    return myIsDefault;
  }

  void setDefault(final boolean isDefault) {
    myIsDefault = isDefault;
  }

  @Override
  public boolean isReadOnly() {
    return myIsReadOnly;
  }

  public void setReadOnlyImpl(final boolean isReadOnly) {
    myIsReadOnly = isReadOnly;
  }

  void setData(@Nullable Object data) {
    myData = data;
  }

  @Nullable
  @Override
  public Object getData() {
    return myData;
  }

  void addChange(Change change) {
    myReadChangesCache = null;
    myChanges.add(change);
    LOG.debug("List: " + myName + ". addChange: " + change);
  }

  @Nullable
  Change removeChange(@Nullable Change change) {
    if (myChanges.remove(change)) {
      LOG.debug("List: " + myName + ". removeChange: " + change);
      myReadChangesCache = null;
      return change;
    }
    return null;
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
    return new LocalChangeListImpl(this, myName);
  }

  public LocalChangeListImpl copy(@NotNull String newName) {
    return new LocalChangeListImpl(this, newName);
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
}
