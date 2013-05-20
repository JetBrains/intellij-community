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

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.impl.FileStatusManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NonNls;

import java.nio.charset.Charset;

import static com.intellij.util.CompressionUtil.compressCharSequence;
import static com.intellij.util.CompressionUtil.uncompressCharSequence;

public class EditorChangeAction extends BasicUndoableAction {
  private final int myOffset;
  private final Object myOldString;
  private final Object myNewString;
  private final long myOldTimeStamp;
  private final long myNewTimeStamp;
  private final Charset myCharset;

  public EditorChangeAction(DocumentEvent e) {
    this((DocumentEx)e.getDocument(), e.getOffset(), e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp());
  }

  public EditorChangeAction(DocumentEx document,
                            int offset,
                            CharSequence oldString,
                            CharSequence newString,
                            long oldTimeStamp) {
    super(document);

    Charset charset = EncodingManager.getInstance().getEncoding(FileDocumentManager.getInstance().getFile(document), false);
    myCharset = charset == null ? Charset.defaultCharset() : charset;

    myOffset = offset;
    myOldString = oldString == null ? "" : compressCharSequence(oldString, myCharset);
    myNewString = newString == null ? "" : compressCharSequence(newString, myCharset);
    myOldTimeStamp = oldTimeStamp;
    myNewTimeStamp = document.getModificationStamp();
  }

  public void undo() {
    DocumentUndoProvider.startDocumentUndo(getDocument());
    try {
      performUndo();
    }
    finally {
      DocumentUndoProvider.finishDocumentUndo(getDocument());
    }

    getDocument().setModificationStamp(myOldTimeStamp);
    refreshFileStatus();
  }

  public void performUndo() {
    exchangeStrings(uncompressCharSequence(myNewString, myCharset), uncompressCharSequence(myOldString, myCharset));
  }

  public void redo() {
    DocumentUndoProvider.startDocumentUndo(getDocument());
    try {
      exchangeStrings(uncompressCharSequence(myOldString, myCharset), uncompressCharSequence(myNewString, myCharset));
    }
    finally {
      DocumentUndoProvider.finishDocumentUndo(getDocument());
    }
    getDocument().setModificationStamp(myNewTimeStamp);
    refreshFileStatus();
  }

  private void exchangeStrings(CharSequence newString, CharSequence oldString) {
    DocumentEx d = getDocument();

    if (newString.length() > 0 && oldString.length() == 0) {
      d.deleteString(myOffset, myOffset + newString.length());
    }
    else if (oldString.length() > 0 && newString.length() == 0) {
      d.insertString(myOffset, oldString);
    }
    else if (oldString.length() > 0 && newString.length() > 0) {
      d.replaceString(myOffset, myOffset + newString.length(), oldString);
    }
  }

  private void refreshFileStatus() {
    VirtualFile f = getAffectedDocuments()[0].getFile();
    if (f == null || f instanceof LightVirtualFile) return;

    for (Project each : ProjectManager.getInstance().getOpenProjects()) {
      FileStatusManagerImpl statusManager = (FileStatusManagerImpl)FileStatusManager.getInstance(each);
      statusManager.refreshFileStatusFromDocument(f, getDocument());
    }
  }


  private DocumentEx getDocument() {
    return (DocumentEx)getAffectedDocuments()[0].getDocument();
  }

  @NonNls
  public String toString() {
    return "editor change: '" + myOldString + "' to '" + myNewString + "'" + " at: " + myOffset;
  }
}

