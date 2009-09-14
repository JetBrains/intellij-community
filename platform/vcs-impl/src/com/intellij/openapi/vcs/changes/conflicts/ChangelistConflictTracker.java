/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.NullableFunction;
import com.intellij.ui.EditorNotificationPanel;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictTracker {

  private static final Key<EditorNotificationPanel> KEY = Key.create("changelistConflictPanel");

  private final Map<String, Conflict> myConflicts = new FactoryMap<String, Conflict>() {
    @Override
    protected Conflict create(String key) {
      return new Conflict();
    }
  };

  private Options myOptions = new Options();
  private final Project myProject;

  private final ChangeListManager myChangeListManager;
  private final ChangeListAdapter myChangeListListener;

  private final FileDocumentManager myDocumentManager;
  private final DocumentAdapter myDocumentListener;

  private final FileStatusManager myFileStatusManager;
  private final FileEditorManager myFileEditorManager;
  private final FileEditorManagerAdapter myFileEditorManagerListener;

  public ChangelistConflictTracker(Project project,
                                   ChangeListManager changeListManager, 
                                   FileStatusManager fileStatusManager) {
    myProject = project;

    myChangeListManager = changeListManager;
    myDocumentManager = FileDocumentManager.getInstance();
    myFileStatusManager = fileStatusManager;
    myFileEditorManager = FileEditorManager.getInstance(project);

    myDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (!myOptions.TRACKING_ENABLED) {
          return;
        }
        Document document = e.getDocument();
        VirtualFile file = myDocumentManager.getFile(document);
        if (file != null && !isWritingAllowed(file)) {
          boolean old = myConflicts.containsKey(file.getPath());
          Conflict conflict = myConflicts.get(file.getPath());
          conflict.timestamp = System.currentTimeMillis();
          conflict.changelistId = myChangeListManager.getDefaultChangeList().getId();
          if (!old) {
            myFileStatusManager.fileStatusChanged(file);
            addNotification(file, true);
          }
        }
      }
    };

    myChangeListListener = new ChangeListAdapter() {
      @Override
      public void changeListChanged(ChangeList list) {
        checkList(list);
      }

      @Override
      public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
        checkList(toList);  
      }

      @Override
      public void changesRemoved(Collection<Change> changes, ChangeList fromList) {
        clearChanges(changes, true);
      }

      @Override
      public void defaultListChanged(ChangeList oldDefaultList, ChangeList newDefaultList) {
        clearChanges(newDefaultList.getChanges(), true);  
      }
    };

    myFileEditorManagerListener = new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(FileEditorManager source, VirtualFile file) {
        if (hasConflict(file)) {
          addNotification(file, true);
        }
      }
    };
  }

  public boolean isWritingAllowed(@NotNull VirtualFile file) {
    if (!myOptions.TRACKING_ENABLED || !myOptions.SHOW_DIALOG) {
      return true;
    }
    LocalChangeList changeList = myChangeListManager.getChangeList(file);
    if (changeList == null || myChangeListManager.isDefaultChangeList(changeList)) {
      return true;
    }
    Conflict conflict = myConflicts.get(file.getPath());
    return conflict == null || conflict.ignored;
  }

  private void addNotification(final VirtualFile file, final boolean add) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        FileEditor[] editors = myFileEditorManager.getEditors(file);
        for (FileEditor editor : editors) {
          EditorNotificationPanel panel = editor.getUserData(KEY);
          if (add && panel == null) {
            panel = new EditorNotificationPanel() {
              {
                myLabel.setText("File from non-active changelist is modified");
              }
              @Override
              protected void executeAction(String actionId) {
                if (actionId.equals("move")) {
                  Change change = myChangeListManager.getChange(file);
                  ChangeList changeList = myChangeListManager.getChangeList(change);
                  MoveChangesDialog dialog =
                    new MoveChangesDialog(myProject, Collections.singletonList(change), Collections.singletonList(changeList),
                                          "Move changes");
                  dialog.show();
                  if (dialog.isOK()) {
                    ChangelistConflictResolution.MOVE.resolveConflict(myProject, dialog.getSelectedChanges());
                  }
                } else if (actionId.equals("switch")) {

                } else if (actionId.equals("ignore")) {
                  ignoreConflict(file, true);
                }
              }
            };
            panel.createActionLabel("Move changes", "move");
            panel.createActionLabel("Switch changelist", "switch");
            panel.createActionLabel("Ignore", "ignore");
            myFileEditorManager.addTopComponent(editor, panel);
            editor.putUserData(KEY, panel);
          } else if (panel != null) {
            myFileEditorManager.removeTopComponent(editor, panel);
            editor.putUserData(KEY, null);
          }
        }
      }
    });
  }

  private void checkList(ChangeList list) {
    clearChanges(list.getChanges(), myChangeListManager.isDefaultChangeList(list));
  }

  private void clearChanges(Collection<Change> changes, boolean removeConflict) {
    for (Change change : changes) {
      ContentRevision revision = change.getAfterRevision();
      if (revision != null) {
        FilePath filePath = revision.getFile();
        String path = filePath.getPath();
        if (removeConflict) {
          myConflicts.remove(path);
          addNotification(filePath.getVirtualFile(), false);
        }
        myFileStatusManager.fileStatusChanged(filePath.getVirtualFile());
      }
    }
  }

  public void startTracking() {
    myChangeListManager.addChangeListListener(myChangeListListener);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener);
    myFileEditorManager.addFileEditorManagerListener(myFileEditorManagerListener);
  }

  public void stopTracking() {
    myChangeListManager.removeChangeListListener(myChangeListListener);
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentListener);
    myFileEditorManager.removeFileEditorManagerListener(myFileEditorManagerListener);
  }

  public void saveState(Element to) {
    for (Map.Entry<String,Conflict> entry : myConflicts.entrySet()) {
      Element fileElement = new Element("file");
      fileElement.setAttribute("path", entry.getKey());
      String id = entry.getValue().changelistId;
      if (id != null) {
        fileElement.setAttribute("changelist", id);
      }
      fileElement.setAttribute("time", Long.toString(entry.getValue().timestamp));
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
      if (path != null) {
        Conflict conflict = new Conflict();
        conflict.changelistId = element.getAttributeValue("changelist");
        try {
          conflict.timestamp = Long.parseLong(element.getAttributeValue("time"));
        }
        catch (NumberFormatException e) {
          // do nothing
        }
        conflict.ignored = Boolean.parseBoolean(element.getAttributeValue("ignored"));
        myConflicts.put(path, conflict);
      }
    }
    XmlSerializer.deserializeInto(myOptions, from);
  }

  public void optionsChanged() {
    for (Map.Entry<String, Conflict> entry : myConflicts.entrySet()) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(entry.getKey());
      if (file != null) {
        myFileStatusManager.fileStatusChanged(file);
        addNotification(file, hasConflict(file));
      }
    }
  }

  public Collection<String> getIgnoredConflicts() {
    return ContainerUtil.mapNotNull(myConflicts.entrySet(), new NullableFunction<Map.Entry<String, Conflict>, String>() {
      public String fun(Map.Entry<String, Conflict> entry) {
        return entry.getValue().ignored ? entry.getKey() : null;
      }
    });
  }

  private static class Conflict {
    long timestamp;
    String changelistId;
    boolean ignored;
  }

  public boolean hasConflict(@NotNull VirtualFile file) {
    if (myOptions.TRACKING_ENABLED) {
      return false;
    }
    Conflict conflict = myConflicts.get(file.getPath());
    return conflict != null && !conflict.ignored;
  }

  public void ignoreConflict(@NotNull VirtualFile file, boolean set) {
    String path = file.getPath();
    Conflict conflict = myConflicts.get(path);
    if (conflict != null) {
      conflict.ignored = set;
    }
    addNotification(file, !set);
    myFileStatusManager.fileStatusChanged(file);
  }

  public Options getOptions() {
    return myOptions;
  }

  public static class Options {
    public boolean TRACKING_ENABLED = true;
    public boolean SHOW_DIALOG = true;
    public boolean HIGHLIGHT_CONFLICTS = true;
    public boolean HIGHLIGHT_NON_ACTIVE_CHANGELIST = false;
    public ChangelistConflictResolution LAST_RESOLUTION = ChangelistConflictResolution.SHELVE;
  }
}