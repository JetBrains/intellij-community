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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ui.UIBundle;
import com.intellij.openapi.editor.Editor;

import javax.swing.*;

/**
 * @author cdr
 */
public class InsertOverwritePanel extends TextPanel implements StatusBarPatch{
  public InsertOverwritePanel() {
    super(false, UIBundle.message("status.bar.column.status.text"), UIBundle.message("status.bar.insert.status.text"), UIBundle.message("status.bar.overwrite.status.text"));
  }

  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    boolean enabled = false;
    if (selected != null) {
      enabled = selected.getDocument().isWritable();

      String text = selected.isColumnMode()
                    ? UIBundle.message("status.bar.column.status.text")
                    : selected.isInsertMode()
                      ? UIBundle.message("status.bar.insert.status.text")
                      : UIBundle.message("status.bar.overwrite.status.text");
      setText(text);
    }
    setEnabled(enabled);
    return null;
  }

  public void clear() {
    setEnabled(false);
    setText("");
  }
}
