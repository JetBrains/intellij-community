/*
 * Copyright 2007-2011 Bas Leijdekkers
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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspection extends BaseInspection {

  private static final JComponent[] EMPTY_COMPONENT_ARRAY = {};

  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousInnerClasses = false;

  @Deprecated @SuppressWarnings({"PublicField"})
  public String superClassString = "java.awt.Component";
  protected List<String> superClassList = new ArrayList();

  @Override
  public final JComponent createOptionsPanel() {
    final JComponent panel = new JPanel(new GridBagLayout());

    final JPanel chooserList = UiUtils.createTreeClassChooserList(
      superClassList, InspectionGadgetsBundle.message("ignore.classes.in.hierarchy.column.name"),
      InspectionGadgetsBundle.message("choose.super.class.to.ignore"));
    UiUtils.setComponentSize(chooserList, 7, 25);
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
      "ignore.anonymous.inner.classes"), this, "ignoreAnonymousInnerClasses");

    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;

    constraints.weightx = 1.0;
    constraints.weighty = 1.0;
    constraints.fill = GridBagConstraints.BOTH;
    panel.add(chooserList, constraints);

    constraints.fill = GridBagConstraints.BOTH;
    final JComponent[] additionalOptions = createAdditionalOptions();
    for (JComponent additionalOption : additionalOptions) {
      constraints.gridy++;
      panel.add(additionalOption, constraints);
    }

    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridy++;
    constraints.weighty = 0.0;
    panel.add(checkBox, constraints);
    return panel;
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    parseString(superClassString, superClassList);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    superClassString = formatString(superClassList);
    super.writeSettings(node);
  }

  protected JComponent[] createAdditionalOptions() {
    return EMPTY_COMPONENT_ARRAY;
  }

  protected boolean isIgnoredSubclass(PsiClass aClass) {
    if (SerializationUtils.isDirectlySerializable(aClass)) {
      return false;
    }
    for (String superClassName : superClassList) {
      if (InheritanceUtil.isInheritor(aClass, superClassName)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getAlternativeID() {
    return "serial";
  }
}