// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChangelistConflictTracker {
  private final Map<String, Conflict> myConflicts = Collections.synchronizedMap(new LinkedHashMap<>());

  private final Options myOptions = new Options();
  private final Project myProject;
  private final ChangeListManager myChangeListManager;

  private final ZipperUpdater myZipperUpdater;

  private final Set<VirtualFile> myCheckSet = new HashSet<>();
  private final AtomicBoolean myShouldIgnoreModifications = new AtomicBoolean(false);

  @NotNull
  public static ChangelistConflictTracker getInstance(@NotNull Project project) {
    return ChangeListManagerImpl.getInstanceImpl(project).getConflictTracker();
  }

  public ChangelistConflictTracker(@NotNull Project project, @NotNull ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;

    myZipperUpdater = new ZipperUpdater(300, Alarm.ThreadToUse.SWING_THREAD, project);
  }

  public void setIgnoreModifications(boolean value) {
    myShouldIgnoreModifications.set(value);
  }

  @RequiresEdt
  private void checkFiles() {
    if (myProject.isDisposed() || !myProject.isOpen()) return;
    if (myCheckSet.isEmpty()) return;

    List<VirtualFile> files = new ArrayList<>(myCheckSet);
    myCheckSet.clear();

    myChangeListManager.invokeAfterUpdate(false, () -> {
      LocalChangeList defaultList = myChangeListManager.getDefaultChangeList();
      for (VirtualFile file : files) {
        checkOneFile(file, defaultList);
      }
    });
  }

  @RequiresBackgroundThread
  private void checkOneFile(@NotNull VirtualFile file, @NotNull LocalChangeList defaultList) {
    if (!shouldDetectConflictsFor(file)) return;

    LocalChangeList changeList = myChangeListManager.getChangeList(file);
    if (changeList == null || Comparing.equal(changeList, defaultList)) {
      return;
    }

    String path = file.getPath();
    boolean newConflict = false;
    synchronized (myConflicts) {
      Conflict conflict = myConflicts.get(path);
      if (conflict == null) {
        conflict = new Conflict();
        myConflicts.put(path, conflict);
        newConflict = true;
      }
    }

    if (newConflict && myOptions.HIGHLIGHT_CONFLICTS) {
      FileStatusManager.getInstance(myProject).fileStatusChanged(file);
      EditorNotifications.getInstance(myProject).updateNotifications(file);
    }
  }

  public boolean isWritingAllowed(@NotNull VirtualFile file) {
    if (isFromActiveChangelist(file)) return true;
    Conflict conflict = myConflicts.get(file.getPath());
    return conflict != null && conflict.ignored;
  }

  public boolean isFromActiveChangelist(VirtualFile file) {
    List<LocalChangeList> changeLists = myChangeListManager.getChangeLists(file);
    return changeLists.isEmpty() || ContainerUtil.exists(changeLists, list -> list.isDefault());
  }

  private boolean shouldDetectConflicts() {
    if (!myOptions.SHOW_DIALOG && !myOptions.HIGHLIGHT_CONFLICTS) return false;
    if (!myChangeListManager.areChangeListsEnabled()) return false;

    AbstractVcs[] activeVcss = ProjectLevelVcsManager.getInstance(myProject).getAllActiveVcss();
    if (activeVcss.length == 0) return false;

    boolean onlyPartialChangelists = LineStatusTrackerManager.getInstance(myProject).arePartialChangelistsEnabled() &&
                                     ContainerUtil.all(activeVcss, vcs -> vcs.arePartialChangelistsSupported());
    return !onlyPartialChangelists;
  }

  private boolean shouldDetectConflictsFor(@NotNull VirtualFile file) {
    AbstractVcs vcs = VcsUtil.getVcsFor(myProject, file);
    if (vcs == null) return false;
    return !LineStatusTrackerManager.getInstance(myProject).arePartialChangelistsEnabled(file);
  }

  @RequiresBackgroundThread
  private void clearChanges(Collection<? extends Change> changes) {
    if (!shouldDetectConflicts() && !myOptions.HIGHLIGHT_NON_ACTIVE_CHANGELIST) return;

    for (Change change : changes) {
      ContentRevision revision = change.getAfterRevision();
      if (revision == null) continue;

      FilePath filePath = revision.getFile();
      String path = filePath.getPath();

      Conflict conflict = myConflicts.remove(path);
      boolean conflictRemoved = conflict != null && !conflict.ignored;

      if (conflictRemoved || myOptions.HIGHLIGHT_NON_ACTIVE_CHANGELIST) {
        VirtualFile file = filePath.getVirtualFile();
        if (file != null) {
          FileStatusManager.getInstance(myProject).fileStatusChanged(file);
          if (conflictRemoved) {
            EditorNotifications.getInstance(myProject).updateNotifications(file);
          }
        }
      }
    }
  }

  public void startTracking() {
    myProject.getMessageBus().connect().subscribe(ChangeListListener.TOPIC, new MyChangeListListener());
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new MyDocumentListener(), myProject);
  }

  public void saveState(Element to) {
    synchronized (myConflicts) {
      for (Map.Entry<String, Conflict> entry : myConflicts.entrySet()) {
        Element fileElement = new Element("file");
        fileElement.setAttribute("path", entry.getKey());
        fileElement.setAttribute("ignored", Boolean.toString(entry.getValue().ignored));
        to.addContent(fileElement);
      }
    }
    XmlSerializer.serializeInto(myOptions, to);
  }

  public void loadState(@NotNull Element from) {
    myConflicts.clear();
    List<Element> files = from.getChildren("file");
    for (Element element : files) {
      String path = element.getAttributeValue("path");
      if (path == null) {
        continue;
      }
      VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
      if (vf == null || myChangeListManager.getChangeList(vf) == null) {
        continue;
      }
      Conflict conflict = new Conflict();
      conflict.ignored = Boolean.parseBoolean(element.getAttributeValue("ignored"));
      myConflicts.put(path, conflict);
    }
    XmlSerializer.deserializeInto(myOptions, from);
  }

  public void optionsChanged() {
    FileStatusManager.getInstance(myProject).fileStatusesChanged();
    EditorNotifications.getInstance(myProject).updateAllNotifications();
  }

  public void clearAllIgnored() {
    for (Conflict conflict : myConflicts.values()) {
      conflict.ignored = false;
    }
  }

  public Collection<String> getIgnoredConflicts() {
    synchronized (myConflicts) {
      return ContainerUtil.mapNotNull(myConflicts.entrySet(), entry -> entry.getValue().ignored ? entry.getKey() : null);
    }
  }

  public static class Conflict {
    boolean ignored;
  }

  public boolean hasConflict(@NotNull VirtualFile file) {
    if (!shouldDetectConflicts()) {
      return false;
    }

    String path = file.getPath();
    Conflict conflict = myConflicts.get(path);
    if (conflict == null || conflict.ignored) {
      return false;
    }

    if (!shouldDetectConflictsFor(file) ||
        isFromActiveChangelist(file)) {
      myConflicts.remove(path);
      return false;
    }

    return true;
  }

  public void ignoreConflict(@NotNull VirtualFile file, boolean ignore) {
    String path = file.getPath();
    Conflict conflict = myConflicts.get(path);
    if (conflict == null) {
      conflict = new Conflict();
      myConflicts.put(path, conflict);
    }
    conflict.ignored = ignore;

    FileStatusManager.getInstance(myProject).fileStatusChanged(file);
    EditorNotifications.getInstance(myProject).updateNotifications(file);
  }

  public Options getOptions() {
    return myOptions;
  }

  private class MyDocumentListener implements BulkAwareDocumentListener.Simple {
    @Override
    public void afterDocumentChange(@NotNull Document document) {
      if (myShouldIgnoreModifications.get() || !shouldDetectConflicts()) {
        return;
      }

      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file == null || !file.isInLocalFileSystem() || ChangesUtil.isInternalOperation(file)) {
        return;
      }

      myCheckSet.add(file);
      myZipperUpdater.queue(() -> checkFiles());
    }
  }

  private class MyChangeListListener implements ChangeListListener {
    @Override
    public void changeListChanged(ChangeList list) {
      if (((LocalChangeList)list).isDefault()) {
        clearChanges(list.getChanges());
      }
    }

    @Override
    public void changesMoved(Collection<? extends Change> changes, ChangeList fromList, ChangeList toList) {
      if (((LocalChangeList)toList).isDefault() || ((LocalChangeList)fromList).isDefault()) {
        clearChanges(changes);
      }
    }

    @Override
    public void changesRemoved(Collection<? extends Change> changes, ChangeList fromList) {
      clearChanges(changes);
    }

    @Override
    public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
      clearChanges(newDefaultList.getChanges());
    }

    @Override
    public void changeListAvailabilityChanged() {
      optionsChanged();
    }
  }

  public static final class Options {
    public boolean SHOW_DIALOG = false;
    public boolean HIGHLIGHT_CONFLICTS = true;
    public boolean HIGHLIGHT_NON_ACTIVE_CHANGELIST = false;
    public ChangelistConflictResolution LAST_RESOLUTION = ChangelistConflictResolution.IGNORE;
  }
}
