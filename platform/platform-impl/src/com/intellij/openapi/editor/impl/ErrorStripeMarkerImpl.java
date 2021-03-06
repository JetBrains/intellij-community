// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import org.jetbrains.annotations.NotNull;

class ErrorStripeMarkerImpl extends RangeMarkerImpl {
  private static final Logger LOG = Logger.getInstance(ErrorStripeMarkerImpl.class);

  private final RangeHighlighterEx myHighlighter;
  private int myLine;

  ErrorStripeMarkerImpl(@NotNull DocumentEx document, @NotNull RangeHighlighterEx highlighter) {
    super(document, highlighter.getStartOffset(), highlighter.getEndOffset(), false, true);
    myHighlighter = highlighter;
    if (highlighter.isPersistent()) {
      myLine = document.getLineNumber(highlighter.getStartOffset());
    }
  }

  @NotNull
  public RangeHighlighterEx getHighlighter() {
    return myHighlighter;
  }

  @Override
  protected void changedUpdateImpl(@NotNull DocumentEvent e) {
    if (myHighlighter.isPersistent()) {
      myLine = persistentHighlighterUpdate(e, myLine, myHighlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE);
    }
    else {
      super.changedUpdateImpl(e);
    }

    validateState(e);
  }

  private void validateState(DocumentEvent e) {
    if (myHighlighter.isValid()) {
      if (!isValid()) {
        LOG.error("Base highlighter " + myHighlighter + " is valid, mirror " + this + " is invalid after " + e);
      }
      else if (intervalStart() != myHighlighter.getStartOffset() || intervalEnd() != myHighlighter.getEndOffset()) {
        LOG.error("Mirror highlighter " + this + " diverged from base one " + myHighlighter + " after " + e);
        setIntervalStart(myHighlighter.getStartOffset());
        setIntervalEnd(myHighlighter.getEndOffset());
        if (myHighlighter.isPersistent()) {
          myLine = getDocument().getLineNumber(intervalStart());
        }
      }
    }
    else if (isValid()) {
      LOG.error("Base highlighter " + myHighlighter + " is invalid, mirror " + this + " is valid after " + e);
      invalidate(e);
    }
  }
}
