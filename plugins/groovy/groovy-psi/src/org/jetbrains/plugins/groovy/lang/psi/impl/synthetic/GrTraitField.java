// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

public class GrTraitField extends GrLightField implements PsiMirrorElement {
  private static final Logger LOG = Logger.getInstance(GrTraitField.class);

  private final PsiField myField;

  public GrTraitField(@NotNull GrField field, GrTypeDefinition clazz, PsiSubstitutor substitutor, @Nullable TransformationContext context) {
    super(clazz, getNewNameForField(field), substitutor.substitute(field.getType()), field);
    GrLightModifierList modifierList = getModifierList();
    for (String modifier : PsiModifier.MODIFIERS) {
      boolean hasModifierProperty;
      GrModifierList fieldModifierList = field.getModifierList();
      if (context == null || fieldModifierList == null) {
        hasModifierProperty = field.hasModifierProperty(modifier);
      } else {
        hasModifierProperty = context.hasModifierProperty(fieldModifierList, modifier);
      }
      if (hasModifierProperty) {
        modifierList.addModifier(modifier);
      }
    }
    modifierList.copyAnnotations(field.getModifierList());
    myField = field;
  }

  @NotNull
  private static String getNewNameForField(@NotNull PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    LOG.assertTrue(containingClass != null);
    return GrTraitUtil.getTraitFieldPrefix(containingClass) + field.getName();
  }

  @NotNull
  @Override
  public PsiField getPrototype() {
    return myField;
  }
}
