/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import javax.swing.*;

/**
 * @author ilyas
 */
public abstract class GrDynamicImplicitElement extends GrImplicitVariableImpl implements GrImplicitVariable, ItemPresentation, NavigationItem {
  public GrDynamicImplicitElement(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public GrDynamicImplicitElement(PsiManager manager, @NonNls String name, @NonNls String type, PsiElement referenceExpression) {
    super(manager, name, type, referenceExpression);
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public String getPresentableText() {
    return null;
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  public PsiClass getContextElement() {
    if (myScope == null) return null;
    if (myScope instanceof GroovyScriptClass) return ((GroovyScriptClass) myScope);

    if (myScope instanceof GrReferenceExpression) {
      final PsiType type = ((GrReferenceExpression) myScope).getType();

      if (!(type instanceof PsiClassType)) return null;

      return ((PsiClassType) type).resolve();
    }

    if (myScope instanceof GrMethodCallExpression) {
      final PsiType type = ((GrMethodCallExpression) myScope).getType();

      if (!(type instanceof PsiClassType)) return null;

      return ((PsiClassType) type).resolve();
    }

    return null;
  }

  @NotNull
  public SearchScope getUseScope() {
    return myScope.getProject().getProjectScope();
  }
}
