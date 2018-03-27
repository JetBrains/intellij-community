/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.awt.*;

public class EmptyClassInspection extends EmptyClassInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(annotationsListControl, constraints);
    constraints.gridy++;
    constraints.weighty = 0.0;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    final CheckBox checkBox1 = new CheckBox(InspectionGadgetsBundle.message("empty.class.ignore.parameterization.option"),
                                                   this, "ignoreClassWithParameterization");
    panel.add(checkBox1, constraints);
    constraints.gridy++;
    final CheckBox checkBox2 = new CheckBox("Ignore subclasses of java.lang.Throwable", this, "ignoreThrowables");
    panel.add(checkBox2, constraints);
    constraints.gridy++;
    final CheckBox checkBox3 = new CheckBox(InspectionGadgetsBundle.message("comments.as.content.option"), this, "commentsAreContent");
    panel.add(checkBox3, constraints);
    return panel;
  }
}