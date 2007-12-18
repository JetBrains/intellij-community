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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightVariableBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;

/**
 * @author ilyas
 */
public class GrImplicitVariable extends LightVariableBase implements org.jetbrains.plugins.groovy.lang.psi.api.statements.GrImplicitVariable, ItemPresentation, NavigationItem {

  public GrImplicitVariable(PsiManager manager, PsiIdentifier nameIdentifier, @NotNull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  public GrImplicitVariable(PsiManager manager, @NonNls String name, @NonNls String type, PsiElement scope) {
    this(manager, null, manager.getElementFactory().createTypeByFQClassName(type, manager.getProject().getAllScope()), false, scope);
    myNameIdentifier = new GrLightIdentifier(myManager, name);
  }


  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitImplicitVariable(this);
  }

  public ItemPresentation getPresentation() {
    return this;
  }


  public String toString() {
    return "Specific implicit variable";
  }

  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public String getPresentableText() {
    return null;
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public Icon getIcon(boolean open) {
    return GroovyIcons.PROPERTY;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  protected class GrLightIdentifier extends LightIdentifier {
    public GrLightIdentifier(PsiManager manager, String name) {
      super(manager, name);
    }
  }
}
