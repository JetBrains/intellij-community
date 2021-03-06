// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.util.CompressionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

final class EditorChangeAction extends BasicUndoableAction {
  private final int myOffset;
  private final int myMoveOffset;
  private final Object myOldString;
  private final Object myNewString;
  private final long myOldTimeStamp;
  private final long myNewTimeStamp;
  private final int myOldLength;
  private final int myNewLength;

  EditorChangeAction(@NotNull DocumentEvent e) {
    this((DocumentImpl)e.getDocument(), e.getOffset(), e.getMoveOffset(), e.getOldFragment(), e.getNewFragment(), e.getOldTimeStamp());
  }

  private EditorChangeAction(@NotNull DocumentImpl document,
                             int offset,
                             int moveOffset,
                             @NotNull CharSequence oldString,
                             @NotNull CharSequence newString,
                             long oldTimeStamp) {
    super(document);
    myOffset = offset;
    myMoveOffset = moveOffset;
    myOldString = CompressionUtil.compressStringRawBytes(oldString);
    myNewString = CompressionUtil.compressStringRawBytes(newString);
    myOldTimeStamp = oldTimeStamp;
    myNewTimeStamp = document.getModificationStamp();
    myNewLength = document.getTextLength();
    myOldLength = myNewLength - newString.length() + oldString.length();
  }

  @Override
  public void undo() throws UnexpectedUndoException {
    doChange(myNewLength, myOldString, myOldLength, myOldTimeStamp);
  }

  @Override
  public void redo() throws UnexpectedUndoException {
    doChange(myOldLength, myNewString, myNewLength, myNewTimeStamp);
  }

  private void doChange(int fromLength, Object to, int toLength, long toTimeStamp) throws UnexpectedUndoException {
    //noinspection ConstantConditions
    DocumentImpl document = (DocumentImpl)getAffectedDocuments()[0].getDocument();
    assert document != null;
    if (document.getTextLength() != fromLength) throw new UnexpectedUndoException("Unexpected document state");

    DocumentUndoProvider.startDocumentUndo(document);
    try {
      CharSequence toString = CompressionUtil.uncompressStringRawBytes(to);
      int fromStringLength = toString.length() - toLength + fromLength;
      document.replaceString(myOffset, myOffset + fromStringLength, myMoveOffset, toString, toTimeStamp, false);
    }
    finally {
      DocumentUndoProvider.finishDocumentUndo(document);
    }
  }

  @Override
  @NonNls
  public String toString() {
    return "editor change: '" + myOldString + "' to '" + myNewString + "'" + " at: " + myOffset;
  }
}

