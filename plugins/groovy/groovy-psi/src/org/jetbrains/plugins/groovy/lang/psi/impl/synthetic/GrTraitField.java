// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

public class GrTraitField extends GrLightField implements PsiMirrorElement {
  private static final Logger LOG = Logger.getInstance(GrTraitField.class);

  private final PsiField myField;

  public GrTraitField(@NotNull GrField field, GrTypeDefinition clazz, PsiSubstitutor substitutor) {
    super(clazz, getNewNameForField(field), substitutor.substitute(field.getType()), field);
    GrLightModifierList modifierList = getModifierList();
    for (String modifier : PsiModifier.MODIFIERS) {
      if (field.hasModifierProperty(modifier)) {
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
