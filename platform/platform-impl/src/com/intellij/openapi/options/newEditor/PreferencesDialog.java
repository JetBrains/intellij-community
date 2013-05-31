/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PreferencesDialog extends DialogWrapper {
  public PreferencesDialog(@Nullable Project project, ConfigurableGroup[] groups) {
    super(project);
    setSize(800, 600);
    init();
    ((JDialog)getPeer().getWindow()).setUndecorated(true);
    ((JComponent)((JDialog)getPeer().getWindow()).getContentPane()).setBorder(new LineBorder(Gray._140, 1));

    setTitle("Preferences");
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel();
    panel.setPreferredSize(new Dimension(800, 600));
    return panel;
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    if (SystemInfo.isMac) {
      return null;
    }
    return super.createSouthPanel();
  }
}
