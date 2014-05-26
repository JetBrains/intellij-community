/*
 * Copyright 2007-2014 Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class SerializableInspectionUtil {
  @NotNull
  public static JComponent createOptions(@NotNull SerializableInspectionBase inspection) {
    final JComponent panel = new JPanel(new GridBagLayout());

    final JPanel chooserList = UiUtils.createTreeClassChooserList(
      inspection.superClassList, InspectionGadgetsBundle.message("ignore.classes.in.hierarchy.column.name"),
      InspectionGadgetsBundle.message("choose.super.class.to.ignore"));
    UiUtils.setComponentSize(chooserList, 7, 25);
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "ignore.anonymous.inner.classes"), inspection, "ignoreAnonymousInnerClasses");

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;

    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(chooserList, constraints);

    final JComponent[] additionalOptions = inspection.createAdditionalOptions();
    for (JComponent additionalOption : additionalOptions) {
      constraints.gridy++;
      if (additionalOption instanceof JPanel) {
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1.0;
      }
      else {
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weighty = 0.0;
      }
      panel.add(additionalOption, constraints);
    }

    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridy++;
    constraints.weighty = 0.0;
    panel.add(checkBox, constraints);
    return panel;
  }
}