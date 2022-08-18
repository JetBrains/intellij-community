// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspectionBase extends BaseInspection {
  private static final JComponent[] EMPTY_COMPONENT_ARRAY = {};
  @SuppressWarnings({"PublicField"})
  public boolean ignoreAnonymousInnerClasses = false;
  @SuppressWarnings({"PublicField"})
  public @NonNls String superClassString = "java.awt.Component";
  protected List<String> superClassList = new ArrayList<>();

  protected SerializableInspectionBase() {
    parseString(superClassString, superClassList);
  }

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
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);

    final JPanel chooserList = UiUtils.createTreeClassChooserList(
      superClassList, InspectionGadgetsBundle.message("ignore.classes.in.hierarchy.column.name"),
      InspectionGadgetsBundle.message("choose.class"));
    UiUtils.setComponentSize(chooserList, 7, 25);

    panel.add(chooserList, "growx, wrap");

    final JComponent[] additionalOptions = createAdditionalOptions();
    for (JComponent additionalOption : additionalOptions) {
      final String constraints = additionalOption instanceof JPanel ? "grow, wrap" : "growx, wrap";
      panel.add(additionalOption, constraints);
    }

    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.anonymous.inner.classes"), "ignoreAnonymousInnerClasses");
    return panel;
  }

  protected JComponent @NotNull [] createAdditionalOptions() {
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
