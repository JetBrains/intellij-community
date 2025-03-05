// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.util.TextRangeScalarUtil;
import org.jetbrains.annotations.NotNull;

final class ErrorStripeMarkerImpl extends RangeMarkerImpl {
  private static final Logger LOG = Logger.getInstance(ErrorStripeMarkerImpl.class);

  private final RangeHighlighterEx myHighlighter;

  ErrorStripeMarkerImpl(@NotNull DocumentEx document, @NotNull RangeHighlighterEx highlighter) {
    super(document, highlighter.getStartOffset(), highlighter.getEndOffset(), false, true);
    myHighlighter = highlighter;
  }

  public @NotNull RangeHighlighterEx getHighlighter() {
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
        if (myHighlighter instanceof PersistentRangeHighlighterImpl h) {
          extendedHighlighterInfo = "(prev state: " + h.getPrevStartOffset() + "-" + h.getPrevEndOffset() + ", stamps match: " +
                                    (h.getModificationStamp() == (byte)e.getDocument().getModificationStamp()) + ")";
        }
        reportError("Mirror highlighter " + this + "(prev state: " + oldStart + "-" + oldEnd +
                    ") diverged from base one " + myHighlighter + extendedHighlighterInfo + " after " + e);
        setRange(TextRangeScalarUtil.toScalarRange(myHighlighter));
      }
    }
    else if (isValid()) {
      reportError("Base highlighter " + myHighlighter + " is invalid, mirror " + this + "(prev state: " + oldStart + "-" + oldEnd +
                  ") is valid after " + e);
      invalidate();
    }
  }

  private void reportError(String message) {
    DocumentEx document = getDocument();
    RangeMarkerTree.RMNode<RangeMarkerEx> node = myHighlighter instanceof RangeHighlighterImpl ? ((RangeHighlighterImpl)myHighlighter).myNode : null;
    LOG.error(message,
              new Attachment("document.txt", document instanceof DocumentImpl ? ((DocumentImpl)document).dumpState() : "" ),
              new Attachment("originalTree.txt", node == null ? "" : node.getTree().dumpState()),
              new Attachment("mirrorTree.txt", myNode != null ? myNode.getTree().dumpState(): "<ErrorStripeMarkerImpl#myNode is null>"));
  }

  @Override
  public String toString() {
    String result = super.toString();
    if (myNode != null && myNode.isFlagSet(RangeHighlighterTree.RHNode.IS_PERSISTENT)) {
      result += "(persistent)";
    }
    return result;
  }
}
