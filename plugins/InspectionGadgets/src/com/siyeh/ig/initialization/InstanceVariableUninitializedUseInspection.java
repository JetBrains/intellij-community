/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.awt.*;

public class InstanceVariableUninitializedUseInspection extends InstanceVariableUninitializedUseInspectionBase {

  public InstanceVariableUninitializedUseInspection() {
  }

  @Override
  public JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());

    final JPanel annotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      annotationNames, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message("primitive.fields.ignore.option"), this, "m_ignorePrimitives");

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(annotationsPanel, constraints);

    constraints.gridy = 1;
    constraints.weighty = 0.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(checkBox, constraints);

    return panel;
  }
}