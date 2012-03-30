/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.methodmetrics;

import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.ui.BlankFiller;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Collections;

public abstract class MethodMetricInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public int m_limit = getDefaultLimit();  //this is public for the DefaultJDOMSerialization thingy

  protected abstract int getDefaultLimit();

  protected abstract String getConfigurationLabel();

  protected final int getLimit() {
    return m_limit;
  }

  @Override
  public final JComponent createOptionsPanel() {
    final String configurationLabel = getConfigurationLabel();
    final JLabel label = new JLabel(configurationLabel);
    final JFormattedTextField valueField = prepareNumberEditor("m_limit");

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0.0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.insets.right = UIUtil.DEFAULT_HGAP;
    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(label, constraints);
    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.insets.right = 0;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    panel.add(valueField, constraints);

    final Collection<? extends JComponent> extraOptions = createExtraOptions();
    if (!extraOptions.isEmpty()) {
      constraints.gridx = 0;
      constraints.gridy = 1;
      constraints.gridwidth = 2;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      for (JComponent option : extraOptions) {
        panel.add(option, constraints);
        constraints.gridy++;
      }
    }
    constraints.gridy++;
    constraints.weighty = 1.0;
    panel.add(new BlankFiller(), constraints);
    return panel;
  }


  public Collection<? extends JComponent> createExtraOptions() {
    return Collections.emptyList();
  }
}
