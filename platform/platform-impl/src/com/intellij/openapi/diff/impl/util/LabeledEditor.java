/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class LabeledEditor extends JPanel {
  private final JLabel myLabel = new JLabel();
  private final Border myEditorBorder;

  public LabeledEditor(@Nullable Border editorBorder) {
    super(new BorderLayout());
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    myEditorBorder = editorBorder;
  }

  public LabeledEditor() {
    this(null);
  }


  private static String addReadOnly(String title, boolean readonly) {
    if (readonly) title += " " + DiffBundle.message("diff.content.read.only.content.title.suffix");
    return title;
  }

  public void setComponent(JComponent component, String title) {
    removeAll();
    final JPanel p = new JPanel(new BorderLayout());
    if (myEditorBorder != null) {
      p.setBorder(myEditorBorder);
    }
    p.add(component, BorderLayout.CENTER);
    add(p, BorderLayout.CENTER);
    add(myLabel, BorderLayout.NORTH);
    setLabelTitle(title);
    revalidate();
  }

  private void setLabelTitle(String title) {
    myLabel.setText(title);
    myLabel.setToolTipText(title);
  }

  public void updateTitle(String title, boolean readonly) {
    setLabelTitle(addReadOnly(title, readonly));
  }
}
