/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;

/**
 * @author ilyas
 */
public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {
  public GrNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "NEW expression";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitNewExpression(this);
  }

  public PsiType getType() {
    PsiType type = null;
    GrCodeReferenceElement refElement = getReferenceElement();
    if (refElement != null) {
      type = new GrClassReferenceType(refElement);
    } else {
      GrBuiltInTypeElement builtin = findChildByClass(GrBuiltInTypeElement.class);
      if (builtin != null) type = builtin.getType();
    }

    if (type != null) {
      for(int i = 0; i < getArrayCount(); i++) {
        type = type.createArrayType();
      }
      return type;
    }

    return null;
  }

  public GrCodeReferenceElement getReferenceElement() {
    return findChildByClass(GrCodeReferenceElement.class);
  }

  public int getArrayCount() {
    return findChildrenByClass(GrArrayDeclarationImpl.class).length;
  }

  @Nullable
  public PsiMethod resolveMethod() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return null;

    final PsiElement resolved = referenceElement.resolve();
    return resolved instanceof PsiMethod ? (PsiMethod) resolved : null;
  }

  @NotNull
  public PsiNamedElement[] getMethodVariants() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return PsiMethod.EMPTY_ARRAY;
    return PsiImplUtil.getMethodVariants(referenceElement);
  }
}
