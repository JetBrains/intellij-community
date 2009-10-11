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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;

import java.awt.event.MouseEvent;
import java.util.EventObject;

public class ErrorStripeEvent extends EventObject {
  private final MouseEvent myMouseEvent;
  private final RangeHighlighter myHighlighter;

  public ErrorStripeEvent(Editor editor, MouseEvent mouseEvent, RangeHighlighter highlighter) {
    super(editor);
    myMouseEvent = mouseEvent;
    myHighlighter = highlighter;
  }

  public Editor getEditor() {
    return (Editor) getSource();
  }

  public MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public RangeHighlighter getHighlighter() {
    return myHighlighter;
  }
}
