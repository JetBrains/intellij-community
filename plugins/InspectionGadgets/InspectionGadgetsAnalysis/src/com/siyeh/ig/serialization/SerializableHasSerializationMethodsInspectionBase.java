/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SerializableHasSerializationMethodsInspectionBase
  extends SerializableInspectionBase {

  protected boolean ignoreClassWithoutFields = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "serializable.has.serialization.methods.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean hasReadObject = ((Boolean)infos[0]).booleanValue();
    final boolean hasWriteObject = ((Boolean)infos[1]).booleanValue();
    if (!hasReadObject && !hasWriteObject) {
      return InspectionGadgetsBundle.message(
        "serializable.has.serialization.methods.problem.descriptor");
    }
    else if (hasReadObject) {
      return InspectionGadgetsBundle.message(
        "serializable.has.serialization.methods.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message(
        "serializable.has.serialization.methods.problem.descriptor2");
    }
  }

  @Override
  protected JComponent[] createAdditionalOptions() {
    return new JComponent[] {new CheckBox(InspectionGadgetsBundle.message("serializable.has.serialization.methods.ignore.option"),
                                          this, "ignoreClassWithoutFields")};
  }

  @Override
  public void readSettings(@NotNull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Element option : node.getChildren("option")) {
      if ("ignoreClassWithoutFields".equals(option.getAttributeValue("name"))) {
        ignoreClassWithoutFields = Boolean.valueOf(option.getAttributeValue("value"));
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (ignoreClassWithoutFields) {
      node.addContent(new Element("option").setAttribute("name", "ignoreClassWithoutFields")
                        .setAttribute("value", String.valueOf(ignoreClassWithoutFields)));
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableHasSerializationMethodsVisitor();
  }

  private class SerializableHasSerializationMethodsVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down
      if (aClass.isInterface() || aClass.isAnnotationType() ||
          aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter ||
          aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      if (ignoreAnonymousInnerClasses &&
          aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      final boolean hasReadObject =
        SerializationUtils.hasReadObject(aClass);
      final boolean hasWriteObject =
        SerializationUtils.hasWriteObject(aClass);
      if (hasWriteObject && hasReadObject) {
        return;
      }
      if (isIgnoredSubclass(aClass)) {
        return;
      }
      if (ignoreClassWithoutFields) {
        final PsiField[] fields = aClass.getFields();
        boolean hasField = false;
        for (PsiField field : fields) {
          if (field.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          hasField = true;
          break;
        }
        if (!hasField) {
          return;
        }
      }
      registerClassError(aClass, Boolean.valueOf(hasReadObject),
                         Boolean.valueOf(hasWriteObject));
    }
  }
}