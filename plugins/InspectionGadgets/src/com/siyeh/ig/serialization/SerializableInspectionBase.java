// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public abstract class SerializableInspectionBase extends BaseInspection {
  private static final JComponent[] EMPTY_COMPONENT_ARRAY = {};
  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousInnerClasses = false;
  @SuppressWarnings({"PublicField"})
  public String superClassString = "java.awt.Component";
  protected List<String> superClassList = new ArrayList<>();

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    parseString(superClassString, superClassList);
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (!superClassList.isEmpty()) {
      superClassString = formatString(superClassList);
    }
    super.writeSettings(node);
  }

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

    final JComponent[] additionalOptions = createAdditionalOptions();
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

  @NotNull
  protected JComponent[] createAdditionalOptions() {
    return EMPTY_COMPONENT_ARRAY;
  }

  protected boolean isIgnoredSubclass(@NotNull PsiClass aClass) {
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
  public final String getAlternativeID() {
    return "serial";
  }
}
