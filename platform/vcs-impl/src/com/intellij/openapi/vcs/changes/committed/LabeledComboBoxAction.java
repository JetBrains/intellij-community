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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public abstract class LabeledComboBoxAction extends ComboBoxAction {

  @NotNull private final String myLabel;

  protected LabeledComboBoxAction(@NotNull String label) {
    myLabel = label;
  }

  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout());

    panel.add(new JLabel(myLabel), BorderLayout.WEST);
    panel.add(super.createCustomComponent(presentation), BorderLayout.CENTER);
    UIUtil.addInsets(panel, JBUI.insets(0, 6, 0, 0));

    return panel;
  }
}
