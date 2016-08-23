/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.Alarm;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictTracker {

  private final Map<String, Conflict> myConflicts = Collections.synchronizedMap(new LinkedHashMap<String, Conflict>());

  private final Options myOptions = new Options();
  private final Project myProject;

  private final ChangeListManager myChangeListManager;
  private final EditorNotifications myEditorNotifications;
  private final ChangeListAdapter myChangeListListener;

  private final FileDocumentManager myDocumentManager;
  private final DocumentAdapter myDocumentListener;

  private final FileStatusManager myFileStatusManager;
  private final Set<VirtualFile> myCheckSet;
  private final Object myCheckSetLock;

  public ChangelistConflictTracker(@NotNull Project project,
                                   @NotNull ChangeListManager changeListManager,
                                   @NotNull FileStatusManager fileStatusManager,
                                   @NotNull EditorNotifications editorNotifications) {
    myProject = project;

    myChangeListManager = changeListManager;
    myEditorNotifications = editorNotifications;
    myDocumentManager = FileDocumentManager.getInstance();
    myFileStatusManager = fileStatusManager;
    myCheckSetLock = new Object();
    myCheckSet = new HashSet<>();

    final Application application = ApplicationManager.getApplication();
    final ZipperUpdater zipperUpdater = new ZipperUpdater(300, Alarm.ThreadToUse.SWING_THREAD, myProject);
    final Runnable runnable = () -> {
      if (application.isDisposed() || myProject.isDisposed() || !myProject.isOpen()) {
        return;
      }
      final Set<VirtualFile> localSet;
      synchronized (myCheckSetLock) {
        localSet = new HashSet<>();
        localSet.addAll(myCheckSet);
        myCheckSet.clear();
      }
      checkFiles(localSet);
    };
    myDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (!myOptions.TRACKING_ENABLED) {
          return;
        }
        Document document = e.getDocument();
        VirtualFile file = myDocumentManager.getFile(document);
        if (ProjectUtil.guessProjectForFile(file) == myProject) {
          synchronized (myCheckSetLock) {
            myCheckSet.add(file);
          }
          zipperUpdater.queue(runnable);
        }
      }
    };

    myChangeListListener = new ChangeListAdapter() {
      @Override
      public void changeListChanged(ChangeList list) {
        if (myChangeListManager.isDefaultChangeList(list)) {
          clearChanges(list.getChanges());
        }
      }

      @Override
      public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
        if (myChangeListManager.isDefaultChangeList(toList)) {
          clearChanges(changes);
        }
      }

      @Override
      public void changesRemoved(Collection<Change> changes, ChangeList fromList) {
        clearChanges(changes);
      }

      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
        clearChanges(newDefaultList.getChanges());
      }
    };
  }

  private void checkFiles(final Collection<VirtualFile> files) {
    myChangeListManager.invokeAfterUpdate(new Runnable() {
      @Override
      public void run() {
        final LocalChangeList list = myChangeListManager.getDefaultChangeList();
        for (VirtualFile file : files) {
          checkOneFile(file, list);
        }
      }
    }, InvokeAfterUpdateMode.SILENT, null, null);
  }

  private void checkOneFile(VirtualFile file, LocalChangeList defaultList) {
    if (file == null) {
      return;
    }
    LocalChangeList changeList = myChangeListManager.getChangeList(file);
    if (changeList == null || Comparing.equal(changeList, defaultList) || ChangesUtil.isInternalOperation(file)) {
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
      myFileStatusManager.fileStatusChanged(file);
      myEditorNotifications.updateNotifications(file);
    }
  }

  public boolean isWritingAllowed(@NotNull VirtualFile file) {
    if (isFromActiveChangelist(file)) return true;
    Conflict conflict = myConflicts.get(file.getPath());
    return conflict != null && conflict.ignored;
  }

  public boolean isFromActiveChangelist(VirtualFile file) {
    LocalChangeList changeList = myChangeListManager.getChangeList(file);
    return changeList == null || myChangeListManager.isDefaultChangeList(changeList);
  }

  private void clearChanges(Collection<Change> changes) {
    for (Change change : changes) {
      ContentRevision revision = change.getAfterRevision();
      if (revision != null) {
        FilePath filePath = revision.getFile();
        String path = filePath.getPath();
        final Conflict wasRemoved = myConflicts.remove(path);
        final VirtualFile file = filePath.getVirtualFile();
        if (wasRemoved != null && file != null) {
          myEditorNotifications.updateNotifications(file);
          // we need to update status
          myFileStatusManager.fileStatusChanged(file);
        }
      }
    }
  }

  public void startTracking() {
    myChangeListManager.addChangeListListener(myChangeListListener);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener, myProject);
  }

  public void stopTracking() {
    myChangeListManager.removeChangeListListener(myChangeListListener);
  }

  public void saveState(Element to) {
    for (Map.Entry<String,Conflict> entry : myConflicts.entrySet()) {
      Element fileElement = new Element("file");
      fileElement.setAttribute("path", entry.getKey());
      fileElement.setAttribute("ignored", Boolean.toString(entry.getValue().ignored));
      to.addContent(fileElement);
    }
    XmlSerializer.serializeInto(myOptions, to);
  }

  public void loadState(Element from) {
    myConflicts.clear();
    List files = from.getChildren("file");
    for (Object file : files) {
      Element element = (Element)file;
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
    for (Map.Entry<String, Conflict> entry : myConflicts.entrySet()) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(entry.getKey());
      if (file != null) {
        myFileStatusManager.fileStatusChanged(file);
        myEditorNotifications.updateNotifications(file);
      }
    }
  }

  public Map<String, Conflict> getConflicts() {
    return myConflicts;
  }

  public Collection<String> getIgnoredConflicts() {
    return ContainerUtil.mapNotNull(myConflicts.entrySet(), new NullableFunction<Map.Entry<String, Conflict>, String>() {
      @Override
      public String fun(Map.Entry<String, Conflict> entry) {
        return entry.getValue().ignored ? entry.getKey() : null;
      }
    });
  }

  public static class Conflict {
    boolean ignored;
  }

  public boolean hasConflict(@NotNull VirtualFile file) {
    if (!myOptions.TRACKING_ENABLED) {
      return false;
    }
    String path = file.getPath();
    Conflict conflict = myConflicts.get(path);
    if (conflict != null && !conflict.ignored) {
      if (isFromActiveChangelist(file)) {
        myConflicts.remove(path);
        return false;
      }
      return true;
    }
    else {
      return false;
    }
  }

  public void ignoreConflict(@NotNull VirtualFile file, boolean ignore) {
    String path = file.getPath();
    Conflict conflict = myConflicts.get(path);
    if (conflict == null) {
      conflict = new Conflict();
      myConflicts.put(path, conflict);
    }
    conflict.ignored = ignore;
    myEditorNotifications.updateNotifications(file);
    myFileStatusManager.fileStatusChanged(file);
  }

  public Project getProject() {
    return myProject;
  }

  public ChangeListManager getChangeListManager() {
    return myChangeListManager;
  }

  public Options getOptions() {
    return myOptions;
  }

  public static class Options {
    public boolean TRACKING_ENABLED = true;
    public boolean SHOW_DIALOG = false;
    public boolean HIGHLIGHT_CONFLICTS = true;
    public boolean HIGHLIGHT_NON_ACTIVE_CHANGELIST = false;
    public ChangelistConflictResolution LAST_RESOLUTION = ChangelistConflictResolution.IGNORE;
  }

}
