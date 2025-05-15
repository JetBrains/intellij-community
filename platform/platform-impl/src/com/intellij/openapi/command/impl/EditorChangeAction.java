// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final MutableActionChangeRange myChangeRange;

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
    ImmutableActionChangeRange immutableActionChangeRange = ImmutableActionChangeRange.Companion.createNew(offset, oldString.length(), newString.length(), oldDocumentLength, newDocumentLength, this);
    myChangeRange = new MutableActionChangeRangeImpl(immutableActionChangeRange);
  }

  @Override
  public void undo() throws UnexpectedUndoException {
    long timeStamp = myChangeRange.isMoved() ? createNextTimeStamp() : myOldTimeStamp;
    ImmutableActionChangeRange range = myChangeRange.getState();
    doChange(range.getNewDocumentLength(), myOldString, range.getOldDocumentLength(), timeStamp);
  }

  @Override
  public void redo() throws UnexpectedUndoException {
    long timeStamp = myChangeRange.isMoved() ? createNextTimeStamp() : myNewTimeStamp;
    ImmutableActionChangeRange range = myChangeRange.getState();
    doChange(range.getOldDocumentLength(), myNewString, range.getNewDocumentLength(), timeStamp);
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
      int offset = myChangeRange.getState().getOffset();
      int moveOffset = myChangeRange.isMoved() ? offset : myMoveOffset;
      document.replaceString(offset, offset + fromStringLength, moveOffset, toString, toTimeStamp, false);
    }
    finally {
      DocumentUndoProvider.finishDocumentUndo(document);
    }
  }

  @Override
  public @NotNull List<MutableActionChangeRange> getChangeRanges(@NotNull DocumentReference reference) {
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
  public @NonNls String toString() {
    String oldString = myOldString.toString().replace("\n", "\\n");
    String newString = myNewString.toString().replace("\n", "\\n");
    return "DocumentChange{%s:'%s'->'%s'}".formatted(myChangeRange.getOffset(), oldString, newString);
  }
}

