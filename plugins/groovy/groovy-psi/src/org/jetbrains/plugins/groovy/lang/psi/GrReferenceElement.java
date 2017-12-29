// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;

/**
 * @author ven
 */
public interface GrReferenceElement<Q extends PsiElement> extends GroovyPsiElement, GroovyPolyVariantReference, GrQualifiedReference<Q> {

  @Override
  @Nullable
  String getReferenceName();

  @Nullable
  String getQualifiedReferenceName();

  @Nullable
  @Override
  default PsiElement resolve() {
    return advancedResolve().getElement();
  }

  @NotNull
  PsiType[] getTypeArguments();

  @Nullable
  GrTypeArgumentList getTypeArgumentList();

  @NotNull
  String getClassNameText();
}
