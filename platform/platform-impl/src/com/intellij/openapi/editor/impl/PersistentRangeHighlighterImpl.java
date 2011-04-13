/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of the markup element for the editor and document.
 * @author max
 */
class PersistentRangeHighlighterImpl extends RangeHighlighterImpl implements RangeHighlighterEx {
  PersistentRangeHighlighterImpl(@NotNull MarkupModel model,
                                 int offset,
                                 int layer,
                                 @NotNull HighlighterTargetArea target,
                                 TextAttributes textAttributes) {
    super(model, model.getDocument().getLineStartOffset(model.getDocument().getLineNumber(offset)), model.getDocument().getLineEndOffset(model.getDocument().getLineNumber(offset)),layer, target, textAttributes,
          false, false);
    setLine(model.getDocument().getLineNumber(offset));
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    DocumentEventImpl event = (DocumentEventImpl)e;
    if (PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, this)) {
      setLine(event.translateLineViaDiff(getLine()));
      if (getLine() < 0 || getLine() >= getDocument().getLineCount()) {
        invalidate(e);
      }
      else {
        DocumentEx document = getDocument();
        setIntervalStart(document.getLineStartOffset(getLine()));
        setIntervalEnd(document.getLineEndOffset(getLine()));
      }
    }
    else {
      super.changedUpdateImpl(e);
      if (isValid()) {
        setLine(getDocument().getLineNumber(getStartOffset()));
        int endLine = getDocument().getLineNumber(getEndOffset());
        if (endLine != getLine()) {
          setIntervalEnd(getDocument().getLineEndOffset(getLine()));
        }
      }
    }
  }

  @Override
  public String toString() {
    return "PersistentRangeHighlighter" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + " - " + getLine() +
           (isGreedyToRight() ? "]" : ")");
  }

  // delegates
  public int getLine() {
    return getData().myLine;
  }

  public void setLine(int line) {
    getData().myLine = line;
  }
}
