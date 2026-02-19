// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion.weighers;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.proximity.ReferenceListWeigher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public final class GrReferenceListWeigher extends ReferenceListWeigher {
  private static final PsiElementPattern.Capture<PsiElement> INSIDE_REFERENCE_LIST =
    PlatformPatterns.psiElement().withParents(GrCodeReferenceElement.class, GrReferenceList.class);

  @Override
  protected Preference getPreferredCondition(final @NotNull PsiElement position) {
    if (INSIDE_REFERENCE_LIST.accepts(position)) {
      GrReferenceList list = (GrReferenceList)position.getParent().getParent();
      PsiElement parent = list.getParent();
      if (parent instanceof GrTypeDefinition cls) {
        if (cls.isInterface() && list == cls.getExtendsClause() || list == cls.getImplementsClause()) {
          return Preference.Interfaces;
        }
        if (list == cls.getExtendsClause()) {
          return Preference.Classes;
        }
      }
      if (parent instanceof GrMethod && ((GrMethod)parent).getThrowsList() == list) {
        return Preference.Exceptions;
      }
    }

    return null;
  }
}
