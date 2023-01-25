// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspectionBase extends BaseInspection {
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
  public @NotNull OptPane getOptionsPane() {
    List<OptRegularComponent> components = new ArrayList<>();
    components.add(OptPane.stringList("superClassList", InspectionGadgetsBundle.message("ignore.classes.in.hierarchy.column.name"),
                                      new JavaClassValidator()));
    components.addAll(getAdditionalOptions().components());
    components.add(OptPane.checkbox("ignoreAnonymousInnerClasses", InspectionGadgetsBundle.message("ignore.anonymous.inner.classes")));
    return new OptPane(components);
  }
  
  protected @NotNull OptPane getAdditionalOptions() {
    return OptPane.EMPTY;
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
