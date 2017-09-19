/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class NamingConventionWithFallbackBean extends NamingConventionBean {
  public boolean inheritDefaultSettings = false;

  public NamingConventionWithFallbackBean(String regex, int minLength, int maxLength) {
    super(regex, minLength, maxLength);
  }

  public boolean isInheritDefaultSettings() {
    return inheritDefaultSettings;
  }

  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JComponent selfOptions = super.createOptionsPanel();
    JBCheckBox inheritCb = new JBCheckBox("Use settings of class naming conventions", inheritDefaultSettings);
    panel.add(inheritCb, BorderLayout.NORTH);
    inheritCb.addActionListener(e -> {
      inheritDefaultSettings = inheritCb.isSelected();
      UIUtil.setEnabled(selfOptions, !inheritDefaultSettings, true);
    });
    panel.add(selfOptions, BorderLayout.CENTER);
    UIUtil.setEnabled(selfOptions, !inheritDefaultSettings, true);
    return panel;
  }
}
