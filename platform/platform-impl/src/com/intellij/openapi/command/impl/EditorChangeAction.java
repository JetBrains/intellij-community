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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.impl.FileStatusManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NonNls;

class EditorChangeAction implements UndoableAction {
  private final DocumentReference myReference;
  private final int myOffset;
  private final CharSequence myOldString;
  private final CharSequence myNewString;
  private final long myTimeStamp;
  private final boolean myBulkUpdate;

  public EditorChangeAction(DocumentEx document,
                            int offset,
                            CharSequence oldString,
                            CharSequence newString,
                            long oldTimeStamp) {
    myReference = DocumentReferenceManager.getInstance().create(document);

    myOffset = offset;
    myOldString = oldString == null ? "" : oldString;
    myNewString = newString == null ? "" : newString;
    myTimeStamp = oldTimeStamp;
    myBulkUpdate = document.isInBulkUpdate();
  }

  public void undo() {
    exchangeStrings(myNewString, myOldString);
    getDocument().setModificationStamp(myTimeStamp);
    refreshFileStatus();
  }

  public void redo() {
    exchangeStrings(myOldString, myNewString);
    refreshFileStatus();
  }

  private void exchangeStrings(CharSequence newString, CharSequence oldString) {
    DocumentEx d = getDocument();

    if (myBulkUpdate) d.setInBulkUpdate(true);

    if (newString.length() > 0 && oldString.length() == 0) {
      d.deleteString(myOffset, myOffset + newString.length());
    }
    else if (oldString.length() > 0 && newString.length() == 0) {
      d.insertString(myOffset, oldString);
    }
    else if (oldString.length() > 0 && newString.length() > 0) {
      d.replaceString(myOffset, myOffset + newString.length(), oldString);
    }

    if (myBulkUpdate) d.setInBulkUpdate(false);
  }

  private void refreshFileStatus() {
    VirtualFile f = myReference.getFile();
    if (f == null || f instanceof LightVirtualFile) return;

    for (Project each : ProjectManager.getInstance().getOpenProjects()) {
      FileStatusManagerImpl statusManager = (FileStatusManagerImpl)FileStatusManager.getInstance(each);
      statusManager.refreshFileStatusFromDocument(f, getDocument());
    }
  }

  public DocumentReference[] getAffectedDocuments() {
    return new DocumentReference[]{myReference};
  }

  public boolean isGlobal() {
    return false;
  }

  private DocumentEx getDocument() {
    return (DocumentEx)myReference.getDocument();
  }

  @NonNls
  public String toString() {
    return "editor change: '" + myOldString + "' to '" + myNewString + "'";
  }
}

