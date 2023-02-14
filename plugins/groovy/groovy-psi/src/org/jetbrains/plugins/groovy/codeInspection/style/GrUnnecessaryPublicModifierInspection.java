// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrRecordUtils;

public class GrUnnecessaryPublicModifierInspection extends GrUnnecessaryModifierInspection {

  public GrUnnecessaryPublicModifierInspection() {
    super(PsiModifier.PUBLIC);
  }

  @Override
  public boolean isRedundant(@NotNull PsiElement element) {
    PsiElement list = element.getParent();
    if (!(list instanceof GrModifierList)) return false;

    PsiElement parent = list.getParent();
    // Do not mark public on fields as unnecessary
    // It may be put there explicitly to prevent getter/setter generation.
    if (parent instanceof GrVariableDeclaration) return false;

    // compact constructors are required to have a visibility modifier
    if (parent instanceof GrMethod && GrRecordUtils.isCompactConstructor((GrMethod)parent)) return false;
    return true;
  }
}
