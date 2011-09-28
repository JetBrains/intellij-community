/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBTableRowEditor extends JPanel {
  public interface RowDocumentListener {
    void documentChanged(DocumentEvent e, int column);
  }

  private MouseEvent myMouseEvent;

  @Override
  public void addNotify() {
    super.addNotify();
    final JComponent c = getPreferredFocusedComponent();
    if (c != null && c.isVisible()) {
      IdeFocusManager.getGlobalInstance().requestFocus(c, true);
    }
  }

  @Nullable
  public final MouseEvent getMouseEvent() {
    return myMouseEvent;
  }

  public final void setMouseEvent(@Nullable MouseEvent e) {
    myMouseEvent = e;
  }

  public abstract void prepareEditor(JTable table, int row);
  public abstract JBTableRow getValue();
  public abstract JComponent getPreferredFocusedComponent();
  public abstract JComponent[] getFocusableComponents();
  public abstract void addDocumentListener(RowDocumentListener listener);
}
