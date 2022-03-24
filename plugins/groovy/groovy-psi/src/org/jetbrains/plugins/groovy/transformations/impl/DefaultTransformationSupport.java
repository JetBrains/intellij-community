// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrRecordUtils;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.Collection;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

public class DefaultTransformationSupport implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (GrRecordUtils.isRecordTransformationApplied(context)) {
      // record's properties have their own names, so default property syntax is inapplicable
      return;
    }
    for (GrField field : context.getFields()) {
      if (!field.isProperty()) continue;
      GrModifierList modifierList = field.getModifierList();
      if (modifierList == null) continue;

      final String fieldName = field.getName();

      String nameNonBoolean = getGetterNameNonBoolean(fieldName);
      if (!hasContradictingMethods(context, nameNonBoolean, false)) {
        context.addMethod(new GrAccessorMethodImpl(field, false, nameNonBoolean));
        if (PsiType.BOOLEAN.equals(field.getDeclaredType())) {
          String nameBoolean = getGetterNameBoolean(fieldName);
          if (!hasContradictingMethods(context, nameBoolean, false)) {
            context.addMethod(new GrAccessorMethodImpl(field, false, nameBoolean));
          }
        }
      }

      if (!context.hasModifierProperty(modifierList, PsiModifier.FINAL)) {
        String setterName = getSetterName(fieldName);
        if (!hasContradictingMethods(context, setterName, true)) {
          context.addMethod(new GrAccessorMethodImpl(field, true, setterName));
        }
      }
    }
  }

  public static boolean hasContradictingMethods(@NotNull TransformationContext helper, String name, boolean isSetter) {
    Collection<PsiMethod> methods = helper.findMethodsByName(name, true);
    final int paramCount = isSetter ? 1 : 0;
    for (PsiMethod method : methods) {
      if (paramCount != method.getParameterList().getParametersCount()) continue;
      if (helper.getCodeClass().equals(method.getContainingClass())) return true;
      PsiModifierList modifierList = method.getModifierList();
      if (modifierList instanceof GrModifierList && helper.hasModifierProperty((GrModifierList)modifierList, PsiModifier.FINAL)) {
        return true;
      }
    }

    return false;
  }
}
