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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GrClassReferenceType extends PsiClassType {
  private final GrCodeReferenceElement myReferenceElement;

  public GrClassReferenceType(GrCodeReferenceElement referenceElement) {
    super(LanguageLevel.JDK_1_5);
    myReferenceElement = referenceElement;
  }
  public GrClassReferenceType(GrCodeReferenceElement referenceElement, LanguageLevel languageLevel) {
    super(languageLevel);
    myReferenceElement = referenceElement;
  }

  @Nullable
  public PsiClass resolve() {
    ResolveResult[] results = multiResolve();
    if (results.length == 1) {
      PsiElement only = results[0].getElement();
      return only instanceof PsiClass ? (PsiClass) only : null;
    }

    return null;
  }

  //reference resolve is cached
  private GroovyResolveResult[] multiResolve() {
    return myReferenceElement.multiResolve(false);
  }

  public String getClassName() {
    return myReferenceElement.getReferenceName();
  }

  @NotNull
  public PsiType[] getParameters() {
    return myReferenceElement.getTypeArguments();
  }

  @NotNull
  public ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      public PsiClass getElement() {
        return resolve();
      }

      public PsiSubstitutor getSubstitutor() {
        final GroovyResolveResult[] results = multiResolve();
        if (results.length != 1) return PsiSubstitutor.UNKNOWN;
        return results[0].getSubstitutor();
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      public boolean isAccessible() {
        final GroovyResolveResult[] results = multiResolve();
        for (GroovyResolveResult result : results) {
          if (result.isAccessible()) return true;
        }
        return false;
      }

      public boolean isStaticsScopeCorrect() {
        return true; //TODO
      }

      public PsiElement getCurrentFileResolveScope() {
        return null; //TODO???
      }

      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @NotNull
  public PsiClassType rawType() {
    final PsiClass clazz = resolve();
    if (clazz != null) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory();
      return factory.createType(clazz, factory.createRawSubstitutor(clazz));
    }

    return this;
  }

  public String getPresentableText() {
    return PsiNameHelper.getPresentableText(myReferenceElement.getReferenceName(), myReferenceElement.getTypeArguments());
  }

  @Nullable
  public String getCanonicalText() {
    PsiClass resolved = resolve();
    if (resolved == null) return null;
    if (resolved instanceof PsiTypeParameter) return resolved.getName();
    final String qName = resolved.getQualifiedName();
    if (isRaw()) return qName;

    final PsiType[] typeArgs = myReferenceElement.getTypeArguments();
    if (typeArgs.length == 0) return qName;

    StringBuilder builder = new StringBuilder();
    builder.append(qName).append("<");
    for (int i = 0; i < typeArgs.length; i++) {
      if (i > 0) builder.append(",");
      final String typeArgCanonical = typeArgs[i].getCanonicalText();
      if (typeArgCanonical != null) {
        builder.append(typeArgCanonical);
      } else {
        return null;
      }
    }
    builder.append(">");
    return builder.toString();
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  public boolean isValid() {
    return myReferenceElement.isValid();
  }

  public boolean equalsToText(@NonNls String text) {
    return text.endsWith(getPresentableText()) && //optimization
        text.equals(getCanonicalText());
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myReferenceElement.getResolveScope();
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    GrClassReferenceType copy = new GrClassReferenceType(myReferenceElement,languageLevel);
    return copy;
  }
}
