// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

/**
 * @author ilyas
 */
public interface GrReferenceExpression extends GrExpression, GrReferenceElement<GrExpression> {

  @Nullable
  GrExpression getQualifierExpression();

  @Nullable
  IElementType getDotTokenType();

  @Nullable
  PsiElement getDotToken();

  @NotNull
  GroovyReference getStaticReference();

  /**
   * @return whether this reference is a receiver of implicit {@code .call()}
   */
  boolean isImplicitCallReceiver();

  boolean hasAt();

  boolean hasMemberPointer();

  /**
   * @return a reference if this expression is an r-value
   */
  @Nullable
  GroovyReference getRValueReference();

  /**
   * @return a reference if this expression is an l-value
   */
  @Nullable
  GroovyReference getLValueReference();
  
  void replaceDotToken(PsiElement newDotToken);

  default GroovyResolveResult @NotNull [] getSameNameVariants() {
    return multiResolve(true);
  }

  GrReferenceExpression bindToElementViaStaticImport(@NotNull PsiMember member);
}
