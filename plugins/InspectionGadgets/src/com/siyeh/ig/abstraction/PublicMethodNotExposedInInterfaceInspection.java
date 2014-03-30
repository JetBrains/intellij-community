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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.awt.*;

public class PublicMethodNotExposedInInterfaceInspection
  extends PublicMethodNotExposedInInterfaceInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
        ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weighty = 1.0;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.CENTER;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(annotationsListControl, constraints);
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "public.method.not.in.interface.option"), this, "onlyWarnIfContainingClassImplementsAnInterface");
    constraints.gridy = 1;
    constraints.weighty = 0.0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(checkBox, constraints);
    return panel;
  }
}