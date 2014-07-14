/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author Bas Leijdekkers
 */
public class RefusedBequestInspection extends RefusedBequestInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(annotations, null);
    final JCheckBox checkBox1 = new CheckBox("Only report when super method is annotated by:", this, "onlyReportWhenAnnotated");
    final CheckBox checkBox2 = new CheckBox(InspectionGadgetsBundle.message("refused.bequest.ignore.empty.super.methods.option"),
                                            this, "ignoreEmptySuperMethods");

    panel.add(checkBox1, BorderLayout.NORTH);
    panel.add(annotationsListControl, BorderLayout.CENTER);
    panel.add(checkBox2, BorderLayout.SOUTH);

    return panel;
  }
}
