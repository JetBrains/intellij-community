package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class LocalChangeListImpl extends LocalChangeList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeList");

  @NotNull private final Project myProject;
  @NotNull private final Set<Change> myChanges;
  private Set<Change> myReadChangesCache = null;
  @NotNull private String myId;
  @NotNull private String myName;
  @NotNull private String myComment = "";
  @Nullable private Object myData;

  private boolean myIsDefault = false;
  private boolean myIsReadOnly = false;
  private OpenTHashSet<Change> myChangesBeforeUpdate;

  @NotNull
  public static LocalChangeListImpl createEmptyChangeListImpl(@NotNull Project project, @NotNull String name) {
    return new LocalChangeListImpl(project, name);
  }

  private LocalChangeListImpl(@NotNull Project project, @NotNull String name) {
    myProject = project;
    myId = UUID.randomUUID().toString();
    myName = validateName(name);

    myChanges = ContainerUtil.newHashSet();
  }

  private LocalChangeListImpl(@NotNull LocalChangeListImpl origin) {
    myId = origin.getId();
    myProject = origin.myProject;
    myName = origin.myName;

    myComment = origin.myComment;
    myIsDefault = origin.myIsDefault;
    myIsReadOnly = origin.myIsReadOnly;
    myData = origin.myData;

    myChanges = ContainerUtil.newHashSet(origin.myChanges);

    if (myChangesBeforeUpdate != null) {
      myChangesBeforeUpdate = new OpenTHashSet<>((Collection<Change>)origin.myChangesBeforeUpdate);
    }

    if (myReadChangesCache != null) {
      myReadChangesCache = ContainerUtil.newHashSet(origin.myReadChangesCache);
    }
  }

  @NotNull
  @Override
  public Set<Change> getChanges() {
    createReadChangesCache();
    return myReadChangesCache;
  }

  private void createReadChangesCache() {
    if (myReadChangesCache == null) {
      myReadChangesCache = Collections.unmodifiableSet(ContainerUtil.newHashSet(myChanges));
    }
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

  @Override
  public void setName(@NotNull String name) {
    myName = validateName(name);
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

  @Override
  public void setComment(@Nullable String comment) {
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

  @Override
  public void setReadOnly(final boolean isReadOnly) {
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

  Collection<Change> startProcessingChanges(final Project project, @Nullable final VcsDirtyScope scope) {
    createReadChangesCache();
    final Collection<Change> result = new ArrayList<>();
    myChangesBeforeUpdate = new OpenTHashSet<>(myChanges);
    for (Change oldBoy : myChangesBeforeUpdate) {
      final ContentRevision before = oldBoy.getBeforeRevision();
      final ContentRevision after = oldBoy.getAfterRevision();
      if (scope == null || before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile())
        || isIgnoredChange(oldBoy, project)) {
        result.add(oldBoy);
        myChanges.remove(oldBoy);
        LOG.debug("List: " + myName + ". removed change during processing: " + oldBoy);
        myReadChangesCache = null;
      }
    }
    return result;
  }

  private static boolean isIgnoredChange(@NotNull Change change, @NotNull Project project) {
    boolean beforeRevIgnored = change.getBeforeRevision() == null || isIgnoredRevision(change.getBeforeRevision(), project);
    boolean afterRevIgnored = change.getAfterRevision() == null || isIgnoredRevision(change.getAfterRevision(), project);
    return beforeRevIgnored && afterRevIgnored;
  }

  private static boolean isIgnoredRevision(final @NotNull ContentRevision revision, final @NotNull Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (project.isDisposed()) {
          return false;
        }
        VirtualFile vFile = revision.getFile().getVirtualFile();
        return vFile != null && ProjectLevelVcsManager.getInstance(project).isIgnored(vFile);
      }
    });
  }

  boolean processChange(@NotNull Change change) {
    LOG.debug("[process change] for '" + myName + "' isDefault: " + myIsDefault + " change: " +
              ChangesUtil.getFilePath(change).getPath());
    if (myIsDefault) {
      LOG.debug("[process change] adding because default");
      addChange(change);
      return true;
    }

    if (myChangesBeforeUpdate.contains(change)) {
      LOG.debug("[process change] adding because equal to old: " + ChangesUtil.getFilePath(change).getPath());
      addChange(change);
      return true;
    }
    LOG.debug("[process change] not found");
    return false;
  }

  boolean doneProcessingChanges(final List<Change> removedChanges, final List<Change> addedChanges) {
    boolean changesDetected = (myChanges.size() != myChangesBeforeUpdate.size());

    for (Change newChange : myChanges) {
      Change oldChange = findOldChange(newChange);
      if (oldChange == null) {
        addedChanges.add(newChange);
      }
    }
    changesDetected |= (! addedChanges.isEmpty());
    final List<Change> removed = new ArrayList<>(myChangesBeforeUpdate);
    // since there are SAME objects...
    removed.removeAll(myChanges);
    removedChanges.addAll(removed);
    changesDetected = changesDetected || (! removedChanges.isEmpty());

    myReadChangesCache = null;
    return changesDetected;
  }

  @Nullable
  private Change findOldChange(final Change newChange) {
    Change oldChange = myChangesBeforeUpdate.get(newChange);
    if (oldChange != null && sameBeforeRevision(oldChange, newChange) &&
        newChange.getFileStatus().equals(oldChange.getFileStatus())) {
      return oldChange;
    }
    return null;
  }

  private static boolean sameBeforeRevision(final Change change1, final Change change2) {
    final ContentRevision b1 = change1.getBeforeRevision();
    final ContentRevision b2 = change2.getBeforeRevision();
    if (b1 != null && b2 != null) {
      final VcsRevisionNumber rn1 = b1.getRevisionNumber();
      final VcsRevisionNumber rn2 = b2.getRevisionNumber();
      final boolean isBinary1 = (b1 instanceof BinaryContentRevision);
      final boolean isBinary2 = (b2 instanceof BinaryContentRevision);
      return rn1 != VcsRevisionNumber.NULL && rn2 != VcsRevisionNumber.NULL && rn1.compareTo(rn2) == 0 && isBinary1 == isBinary2;
    }
    return b1 == null && b2 == null;
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
  public LocalChangeList copy() {
    return new LocalChangeListImpl(this);
  }

  public void setId(@NotNull String id) {
    myId = id;
  }
}
