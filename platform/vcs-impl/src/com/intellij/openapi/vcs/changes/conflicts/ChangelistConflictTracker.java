/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictTracker {

  private final Map<String, Conflict> myConflicts = new LinkedHashMap<String, Conflict>();

  private final Options myOptions = new Options();
  private final Project myProject;

  private final ChangeListManager myChangeListManager;
  private final EditorNotifications myEditorNotifications;
  private final ChangeListAdapter myChangeListListener;

  private final FileDocumentManager myDocumentManager;
  private final DocumentAdapter myDocumentListener;

  private final FileStatusManager myFileStatusManager;

  public ChangelistConflictTracker(Project project,
                                   ChangeListManager changeListManager, 
                                   FileStatusManager fileStatusManager,
                                   EditorNotifications editorNotifications) {
    myProject = project;

    myChangeListManager = changeListManager;
    myEditorNotifications = editorNotifications;
    myDocumentManager = FileDocumentManager.getInstance();
    myFileStatusManager = fileStatusManager;

    myDocumentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (!myOptions.TRACKING_ENABLED) {
          return;
        }
        Document document = e.getDocument();
        final VirtualFile file = myDocumentManager.getFile(document);
        if (file == null || isFromActiveChangelist(file) || ChangesUtil.isInternalOperation(file)) {
          return;
        }
        myChangeListManager.invokeAfterUpdate(new Runnable() {
          public void run() {

            if (!isFromActiveChangelist(file)) {
              String path = file.getPath();
              Conflict conflict = myConflicts.get(path);
              boolean newConflict = false;
              if (conflict == null) {
                conflict = new Conflict();
                myConflicts.put(path, conflict);
                newConflict = true;
              }
              conflict.timestamp = System.currentTimeMillis();
              conflict.changelistId = myChangeListManager.getDefaultChangeList().getId();

              if (newConflict && myOptions.HIGHLIGHT_CONFLICTS) {
                myFileStatusManager.fileStatusChanged(file);
                myEditorNotifications.updateNotifications(file);
              }
            }
          }
        }, InvokeAfterUpdateMode.SILENT, null, null);
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
  }

  public boolean isWritingAllowed(@NotNull VirtualFile file) {
    if (isFromActiveChangelist(file)) return true;
    Conflict conflict = myConflicts.get(file.getPath());
    return conflict != null && conflict.ignored;
  }

  public boolean isFromActiveChangelist(VirtualFile file) {
    LocalChangeList changeList = myChangeListManager.getChangeList(file);
    if (changeList == null || myChangeListManager.isDefaultChangeList(changeList)) {
      return true;
    }
    return false;
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
          myEditorNotifications.updateNotifications(filePath.getVirtualFile());
        }
        myFileStatusManager.fileStatusChanged(filePath.getVirtualFile());
      }
    }
  }

  public void startTracking() {
    myChangeListManager.addChangeListListener(myChangeListListener);
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myDocumentListener);
  }

  public void stopTracking() {
    myChangeListManager.removeChangeListListener(myChangeListListener);
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myDocumentListener);
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
        myEditorNotifications.updateNotifications(file);
      }
    }
  }

  public Map<String, Conflict> getConflicts() {
    return myConflicts;
  }

  public Collection<String> getIgnoredConflicts() {
    return ContainerUtil.mapNotNull(myConflicts.entrySet(), new NullableFunction<Map.Entry<String, Conflict>, String>() {
      public String fun(Map.Entry<String, Conflict> entry) {
        return entry.getValue().ignored ? entry.getKey() : null;
      }
    });
  }

  public static class Conflict {
    long timestamp;
    String changelistId;
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
