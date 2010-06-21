/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.ModifierFlags;
import com.intellij.psi.impl.cache.RecordUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import javax.swing.*;
import java.util.Set;

/**
 * @author ven
 */
public class GrAccessorMethodImpl extends GrSyntheticMethod implements GrAccessorMethod {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl");
  @NotNull private final GrField myProperty;

  private final boolean myIsSetter;

  public GrAccessorMethodImpl(@NotNull GrField property, boolean isSetter, String name) {
    super(property.getManager(), name);
    myProperty = property;
    myIsSetter = isSetter;
  }

  @Nullable
  public PsiType getReturnType() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getType();
  }

  @Nullable
  public PsiType getInferredReturnType() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getTypeGroovy();
  }


  protected GrLightParameter[] getParameters() {
    if (myIsSetter) {
      PsiType type = myProperty.getDeclaredType();
      if (type == null) {
        type = TypesUtil.getJavaLangObject(myProperty);
      }
      //return LightParameter.EMPTY_ARRAY;
      return new GrLightParameter[]{new GrLightParameter(myProperty.getName(), type, this)};
    }

    return GrLightParameter.EMPTY_ARRAY;
  }

  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return myProperty.getNameIdentifier();
  }

  protected Set<String> getModifiers() {
    int modifiers = ModifierFlags.PUBLIC_MASK;
    final PsiModifierList original = myProperty.getModifierList();
    assert original != null;
    if (original.hasExplicitModifier(PsiModifier.STATIC)) modifiers |= ModifierFlags.STATIC_MASK;
    if (original.hasExplicitModifier(PsiModifier.FINAL)) modifiers |= ModifierFlags.FINAL_MASK;
    return RecordUtil.getModifierSet(modifiers);
  }

  public PsiClass getContainingClass() {
    return myProperty.getContainingClass();
  }

  public int getTextOffset() {
    return myProperty.getTextOffset();
  }

  public PsiElement copy() {
    //return new GrAccessorMethodImpl(myProperty, myIsSetter, getName());
    //rename refactoring may create a copy using this method, add it to a class to check for conflicts, and then remove this copy.
    final String modifiers = getModifierList().getText();
    final String params;
    if (myIsSetter) {
      params="("+myProperty.getName()+")";
    }
    else {
      params="()";
    }
    return GroovyPsiElementFactory.getInstance(getProject()).createMethodFromText(modifiers+" "+getName()+params+"{}");
  }

  public PsiFile getContainingFile() {
    return myProperty.getContainingFile();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myProperty;
  }

  public String toString() {
    return "AccessorMethod";
  }

  public boolean isValid() {
    return myProperty.isValid();
  }

  @NotNull
  public GrField getProperty() {
    return myProperty;
  }

  @Override
  public PsiElement getContext() {
    return myProperty;
  }

  @Override
  public Icon getIcon(int flags) {
    return GroovyIcons.PROPERTY;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (another == this) return true;
    if (!(another instanceof GrAccessorMethod)) return false;

    if (!((GrAccessorMethod)another).getName().equals(getName())) return false;
    return getManager().areElementsEquivalent(myProperty, ((GrAccessorMethod)another).getProperty());
  }
}
