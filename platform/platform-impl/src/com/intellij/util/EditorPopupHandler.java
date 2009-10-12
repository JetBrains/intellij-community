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
package com.intellij.util;

import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;

public abstract class EditorPopupHandler extends EditorMouseAdapter {
  public abstract void invokePopup(EditorMouseEvent event);

  private void handle(EditorMouseEvent e) {
    if (e.getMouseEvent().isPopupTrigger() && e.getArea() == EditorMouseEventArea.EDITING_AREA) {
      invokePopup(e);
      e.consume();
    }
  }

  public void mouseClicked(EditorMouseEvent e) {
    handle(e);
  }

  public void mousePressed(EditorMouseEvent e) {
    handle(e);
  }

  public void mouseReleased(EditorMouseEvent e) {
    handle(e);
  }
}