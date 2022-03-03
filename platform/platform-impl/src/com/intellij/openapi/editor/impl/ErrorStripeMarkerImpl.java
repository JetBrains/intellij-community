// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import org.jetbrains.annotations.NotNull;

class ErrorStripeMarkerImpl extends RangeMarkerImpl {
  private static final Logger LOG = Logger.getInstance(ErrorStripeMarkerImpl.class);

  private final RangeHighlighterEx myHighlighter;

  ErrorStripeMarkerImpl(@NotNull DocumentEx document, @NotNull RangeHighlighterEx highlighter) {
    super(document, highlighter.getStartOffset(), highlighter.getEndOffset(), false, true);
    myHighlighter = highlighter;
  }

  @NotNull
  public RangeHighlighterEx getHighlighter() {
    return myHighlighter;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    int oldStart = intervalStart();
    int oldEnd = intervalEnd();

    if (myHighlighter.isPersistent()) {
      persistentHighlighterUpdate(e, myHighlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE);
    }
    else {
      super.changedUpdateImpl(e);
    }

    validateState(e, oldStart, oldEnd);
  }

  private void validateState(DocumentEvent e, int oldStart, int oldEnd) {
    if (myHighlighter.isValid()) {
      if (!isValid()) {
        reportError("Base highlighter " + myHighlighter + " is valid, mirror " +
                    this + "(prev state: " + oldStart + "-" + oldEnd + ") is invalid after " + e);
      }
      else if (intervalStart() != myHighlighter.getStartOffset() || intervalEnd() != myHighlighter.getEndOffset()) {
        String extendedHighlighterInfo = "";
        if (myHighlighter instanceof PersistentRangeHighlighterImpl) {
          PersistentRangeHighlighterImpl h = (PersistentRangeHighlighterImpl)myHighlighter;
          extendedHighlighterInfo = "(prev state: " + h.prevStartOffset + "-" + h.prevEndOffset + ", stamps match: " +
                                    (h.modificationStamp == (byte)e.getDocument().getModificationStamp()) + ")";
        }
        reportError("Mirror highlighter " + this + "(prev state: " + oldStart + "-" + oldEnd +
                    ") diverged from base one " + myHighlighter + extendedHighlighterInfo + " after " + e);
        setIntervalStart(myHighlighter.getStartOffset());
        setIntervalEnd(myHighlighter.getEndOffset());
      }
    }
    else if (isValid()) {
      reportError("Base highlighter " + myHighlighter + " is invalid, mirror " + this + "(prev state: " + oldStart + "-" + oldEnd +
                  ") is valid after " + e);
      invalidate(e);
    }
  }

  private void reportError(String message) {
    DocumentEx document = getDocument();
    LOG.error(message,
              new Attachment("document.txt", document instanceof DocumentImpl ? ((DocumentImpl)document).dumpState() : "" ),
              new Attachment("originalTree.txt", myHighlighter instanceof RangeHighlighterImpl ?
                                                 ((RangeHighlighterImpl)myHighlighter).myNode.getTree().dumpState() : ""),
              new Attachment("mirrorTree.txt", myNode.getTree().dumpState()));
  }

  @Override
  public String toString() {
    String result = super.toString();
    if (myNode.isFlagSet(RangeHighlighterTree.RHNode.IS_PERSISTENT)) {
      result += "(persistent)";
    }
    return result;
  }
}
