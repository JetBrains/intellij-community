// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public final class PsiElementCategory implements PsiEnhancerCategory {

  public static @Nullable PsiElement bind(PsiElement element) {
    PsiElement elem = element instanceof GrMethodCall ? ((GrMethodCall)element).getInvokedExpression() : element;
    final PsiReference ref = elem.getReference();
    return ref == null ? null : ref.resolve();
  }

  public static @Nullable PsiElement getQualifier(PsiElement elem){
    if (elem instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)elem).getQualifierExpression();
    }
    return null;
  }

  public static @NotNull Collection<? extends PsiElement> asList(@Nullable PsiElement elem) {
    if (elem == null) return new ArrayList<>();
    if (elem instanceof GrListOrMap) {
      return Arrays.asList(((GrListOrMap)elem).getInitializers());
    } else if (elem instanceof GrAnnotationArrayInitializer){
      return Arrays.asList(((GrAnnotationArrayInitializer)elem).getInitializers());
    } else {
      return Collections.singleton(elem);
    }
  }

  public static Object eval(PsiElement elem) {
    if (elem instanceof GrLiteral literal) {
      return literal.getValue();
    }
    return elem;
  }

}
