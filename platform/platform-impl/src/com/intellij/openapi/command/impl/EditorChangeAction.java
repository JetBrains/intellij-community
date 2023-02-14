// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CompressionUtil;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

final class EditorChangeAction extends BasicUndoableAction implements AdjustableUndoableAction {
  private final int myMoveOffset;
  private final Object myOldString;
  private final Object myNewString;
  private final long myOldTimeStamp;
  private final long myNewTimeStamp;
  private final ActionChangeRangeImpl myChangeRange;

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
    myMoveOffset = moveOffset;
    myOldString = CompressionUtil.compressStringRawBytes(oldString);
    myNewString = CompressionUtil.compressStringRawBytes(newString);
    myOldTimeStamp = oldTimeStamp;
    myNewTimeStamp = document.getModificationStamp();
    int newDocumentLength = document.getTextLength();
    int oldDocumentLength = newDocumentLength - newString.length() + oldString.length();
    myChangeRange = new ActionChangeRangeImpl(offset, oldString.length(), newString.length(), oldDocumentLength, newDocumentLength, this);
  }

  @Override
  public void undo() throws UnexpectedUndoException {
    long timeStamp = myChangeRange.isMoved() ? createNextTimeStamp() : myOldTimeStamp;
    doChange(myChangeRange.getNewDocumentLength(), myOldString, myChangeRange.getOldDocumentLength(), timeStamp);
  }

  @Override
  public void redo() throws UnexpectedUndoException {
    long timeStamp = myChangeRange.isMoved() ? createNextTimeStamp() : myNewTimeStamp;
    doChange(myChangeRange.getOldDocumentLength(), myNewString, myChangeRange.getNewDocumentLength(), timeStamp);
  }

  private static long createNextTimeStamp() {
    return LocalTimeCounter.currentTime();
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
      int offset = myChangeRange.getOffset();
      int moveOffset = myChangeRange.isMoved() ? offset : myMoveOffset;
      document.replaceString(offset, offset + fromStringLength, moveOffset, toString, toTimeStamp, false);
    }
    finally {
      DocumentUndoProvider.finishDocumentUndo(document);
    }
  }

  @Override
  public @NotNull List<ActionChangeRange> getChangeRanges(@NotNull DocumentReference reference) {
    return isAffected(reference) ? Collections.singletonList(myChangeRange) : Collections.emptyList();
  }

  private boolean isAffected(@NotNull DocumentReference reference) {
    // `DocumentReference.getDocument()` can throw if it refers to a deleted file
    // (see an implementation for `DocumentReferenceByVirtualFile`),
    // so it's safer to compare two virtual files first
    DocumentReference affected = getAffectedDocuments()[0];
    VirtualFile affectedFile = affected.getFile();
    if (affectedFile != null) {
      return affectedFile.equals(reference.getFile());
    } else {
      return affected.getDocument() == reference.getDocument();
    }
  }

  @Override
  public void invalidateChangeRanges() {
    myChangeRange.invalidate();
  }

  @Override
  @NonNls
  public String toString() {
    return "editor change: '" + myOldString + "' to '" + myNewString + "'" + " at: " + myChangeRange.getOffset();
  }
}

