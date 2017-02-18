/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

/**
 * @author ven
 */
public class GrAccessorMethodImpl extends LightMethodBuilder implements GrAccessorMethod {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl");
  @NotNull private final GrField myProperty;

  private final boolean myIsSetter;

  public GrAccessorMethodImpl(@NotNull GrField property, boolean isSetter, String name) {
    super(property.getManager(), GroovyLanguage.INSTANCE, name,
          new LightParameterListBuilder(property.getManager(), GroovyLanguage.INSTANCE),
          new LightModifierList(property.getManager()) {
            @Override
            public String getText() {
              final String[] modifiers = getModifiers();
              if (modifiers.length == 0) return "";
              if (modifiers.length == 1) return modifiers[0];

              return StringUtil.join(modifiers, " ");
            }
          });
    myProperty = property;
    myIsSetter = isSetter;

    if (myIsSetter) {
      PsiType type = myProperty.getDeclaredType();
      if (type == null) {
        type = TypesUtil.getJavaLangObject(myProperty);
      }
      addParameter(myProperty.getName(), type);
    }

    setMethodReturnType(myIsSetter ? PsiType.VOID : myProperty.getType());

    addModifier(PsiModifier.PUBLIC);
    if (myProperty.hasModifierProperty(PsiModifier.STATIC)) {
      addModifier(PsiModifier.STATIC);
    }
    else if (myProperty.hasModifierProperty(PsiModifier.FINAL)) { //don't add final modifier to static method
      addModifier(PsiModifier.FINAL);
    }

    if (myProperty.hasModifierProperty(PsiModifier.ABSTRACT) && GrTraitUtil.isTrait(myProperty.getContainingClass())) {
      addModifier(PsiModifier.ABSTRACT);
    }

    setNavigationElement(property);
    setBaseIcon(JetgroovyIcons.Groovy.Property);

    setContainingClass(myProperty.getContainingClass());
    setMethodKind("AccessorMethod");
    setOriginInfo("synthetic accessor for '"+myProperty.getName()+"'");
  }

  @Override
  @Nullable
  public PsiType getInferredReturnType() {
    if (myIsSetter) return PsiType.VOID;
    return myProperty.getTypeGroovy();
  }

  @Override
  public boolean isSetter() {
    return myIsSetter;
  }


  @Override
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

  @Override
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

  @NotNull
  @Override
  public PsiElement getPrototype() {
    return getProperty();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    if (GrTraitUtil.isTrait(getContainingClass())) {
      if (PsiModifier.ABSTRACT.equals(name)) return true;
      if (PsiModifier.FINAL.equals(name)) return false;
    }
    return super.hasModifierProperty(name);
  }
}
