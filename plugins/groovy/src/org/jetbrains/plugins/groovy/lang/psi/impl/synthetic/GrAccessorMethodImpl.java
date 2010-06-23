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
import com.intellij.psi.impl.light.LightMethodBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ven
 */
public class GrAccessorMethodImpl extends LightMethodBuilder implements GrAccessorMethod {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl");
  @NotNull private final GrField myProperty;

  private final boolean myIsSetter;

  public GrAccessorMethodImpl(@NotNull GrField property, boolean isSetter, String name) {
    super(property.getManager(), GroovyFileType.GROOVY_LANGUAGE, name);
    myProperty = property;
    myIsSetter = isSetter;

    if (myIsSetter) {
      PsiType type = myProperty.getDeclaredType();
      if (type == null) {
        type = TypesUtil.getJavaLangObject(myProperty);
      }
      addParameter(myProperty.getName(), type);
    }

    setReturnType(myIsSetter ? PsiType.VOID : myProperty.getType());

    addModifier(PsiModifier.PUBLIC);
    if (myProperty.hasModifierProperty(PsiModifier.STATIC)) {
      addModifier(PsiModifier.STATIC);
    }
    if (myProperty.hasModifierProperty(PsiModifier.FINAL)) {
      addModifier(PsiModifier.FINAL);
    }

    setNavigationElement(property);
    setBaseIcon(GroovyIcons.PROPERTY);

    setContainingClass(myProperty.getContainingClass());
    setMethodKind("AccessorMethod");
  }

  @Nullable
  public PsiType getInferredReturnType() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getTypeGroovy();
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

  @NotNull
  public GrField getProperty() {
    return myProperty;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (another == this) return true;
    if (!(another instanceof GrAccessorMethod)) return false;

    if (!((GrAccessorMethod)another).getName().equals(getName())) return false;
    return getManager().areElementsEquivalent(myProperty, ((GrAccessorMethod)another).getProperty());
  }
}
