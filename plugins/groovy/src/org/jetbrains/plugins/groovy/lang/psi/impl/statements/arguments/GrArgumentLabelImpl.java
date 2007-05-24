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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import java.util.List;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GrArgumentLabelImpl extends GroovyPsiElementImpl implements GrArgumentLabel, PsiReference {

  public GrArgumentLabelImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Argument label";
  }

  public PsiReference getReference() {
    return this;
  }

  public PsiElement getElement() {
    return this;
  }

  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Nullable
  public PsiElement resolve() {
    String setterName = PropertyUtil.suggestSetterName(getText());
    PsiElement context = getParent().getParent();
    if (context instanceof GrArgumentList) {
      PsiType type = ((GrExpression) context.getParent()).getType();
      if (type instanceof PsiClassType) {
        PsiClass clazz = ((PsiClassType) type).resolve();
        if (clazz != null) {
          PsiMethod[] byName = clazz.findMethodsByName(setterName, true);
          if (byName.length > 0) return byName[0];
        }
      }
    }
    return null;
  }

  public String getCanonicalText() {
    PsiElement resolved = resolve();
    if (resolved instanceof PsiMember && resolved instanceof PsiNamedElement) {
      PsiClass clazz = ((PsiMember) resolved).getContainingClass();
      if (clazz != null) {
        String qName = clazz.getQualifiedName();
        if (qName != null) {
          return qName + "." + ((PsiNamedElement) resolved).getName();
        }
      }
    }

    return getText();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY");
  }

  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiMethod) && !(element instanceof PsiField)) return false;
    return element.equals(resolve());
  }

  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public boolean isSoft() {
    return false;
  }
}