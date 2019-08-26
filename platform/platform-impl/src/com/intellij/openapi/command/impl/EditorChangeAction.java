// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CompressionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class EditorChangeAction extends BasicUndoableAction {
  private final int myOffset;
  private final Object myOldString;
  private final Object myNewString;
  private final long myOldTimeStamp;
  private final long myNewTimeStamp;
  private final int myOldLength;
  private final int myNewLength;

  public EditorChangeAction(@NotNull DocumentEvent e) {
    this((DocumentEx)e.getDocument(), e.getOffset(), e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp());
  }

  public EditorChangeAction(@NotNull DocumentEx document,
                            int offset,
                            @NotNull CharSequence oldString,
                            @NotNull CharSequence newString,
                            long oldTimeStamp) {
    super(document);
    myOffset = offset;
    myOldString = CompressionUtil.compressStringRawBytes(oldString);
    myNewString = CompressionUtil.compressStringRawBytes(newString);
    myOldTimeStamp = oldTimeStamp;
    myNewTimeStamp = document.getModificationStamp();
    myNewLength = document.getTextLength();
    myOldLength = myNewLength - newString.length() + oldString.length();
  }

  @Override
  public void undo() throws UnexpectedUndoException {
    validateDocumentLength(myNewLength);
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
    CharSequence oldString = CompressionUtil.uncompressStringRawBytes(myOldString);
    CharSequence newString = CompressionUtil.uncompressStringRawBytes(myNewString);
    exchangeStrings(newString, oldString);
  }

  @Override
  public void redo() throws UnexpectedUndoException {
    validateDocumentLength(myOldLength);
    DocumentUndoProvider.startDocumentUndo(getDocument());
    try {
      CharSequence oldString = CompressionUtil.uncompressStringRawBytes(myOldString);
      CharSequence newString = CompressionUtil.uncompressStringRawBytes(myNewString);
      exchangeStrings(oldString, newString);
    }
    finally {
      DocumentUndoProvider.finishDocumentUndo(getDocument());
    }
    getDocument().setModificationStamp(myNewTimeStamp);
    refreshFileStatus();
  }

  private void exchangeStrings(@NotNull CharSequence newString, @NotNull CharSequence oldString) {
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

  private void validateDocumentLength(int expectedLength) throws UnexpectedUndoException {
    if (getDocument().getTextLength() != expectedLength) throw new UnexpectedUndoException("Unexpected document state");
  }

  private void refreshFileStatus() {
    VirtualFile f = getAffectedDocuments()[0].getFile();
    if (f == null || f instanceof LightVirtualFile) return;

    for (Project each : ProjectManager.getInstance().getOpenProjects()) {
      FileStatusManager statusManager = FileStatusManager.getInstance(each);
      statusManager.refreshFileStatusFromDocument(f, getDocument());
    }
  }


  private DocumentEx getDocument() {
    return (DocumentEx)getAffectedDocuments()[0].getDocument();
  }

  @Override
  @NonNls
  public String toString() {
    return "editor change: '" + myOldString + "' to '" + myNewString + "'" + " at: " + myOffset;
  }
}

