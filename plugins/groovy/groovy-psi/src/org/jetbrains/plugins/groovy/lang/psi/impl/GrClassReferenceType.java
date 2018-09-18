// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import static com.intellij.openapi.util.text.StringUtil.notNullize;

/**
 * @author ven
 */
public class GrClassReferenceType extends PsiClassType {
  private final GrReferenceElement myReferenceElement;

  public GrClassReferenceType(GrReferenceElement referenceElement) {
    super(LanguageLevel.JDK_1_5);
    myReferenceElement = referenceElement;
  }
  public GrClassReferenceType(GrReferenceElement referenceElement, @NotNull LanguageLevel languageLevel) {
    super(languageLevel);
    myReferenceElement = referenceElement;
  }

  @Override
  @Nullable
  public PsiClass resolve() {
    return resolveGenerics().getElement();
  }

  @Override
  @Nullable
  public String getClassName() {
    final PsiClass resolved = resolve();
    if (resolved != null) return resolved.getName();
    return myReferenceElement.getReferenceName();
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    return myReferenceElement.getTypeArguments();
  }

  @Override
  @NotNull
  public ClassResolveResult resolveGenerics() {
    final GroovyResolveResult resolveResult = myReferenceElement.advancedResolve();
    return new ClassResolveResult() {
      @Override
      public PsiClass getElement() {
        final PsiElement resolved = resolveResult.getElement();
        return resolved instanceof PsiClass ? (PsiClass)resolved : null;
      }

      @Override
      @NotNull
      public PsiSubstitutor getSubstitutor() {
        return resolveResult.getSubstitutor();
      }

      @Override
      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      @Override
      public boolean isAccessible() {
        return resolveResult.isAccessible();
      }

      @Override
      public boolean isStaticsScopeCorrect() {
        return resolveResult.isStaticsOK();
      }

      @Override
      @Nullable
      public PsiElement getCurrentFileResolveScope() {
        return resolveResult.getCurrentFileResolveContext();
      }

      @Override
      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @Override
  @NotNull
  public PsiClassType rawType() {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myReferenceElement.getProject());

    final PsiClass clazz = resolve();
    if (clazz != null) {
      return factory.createType(clazz, factory.createRawSubstitutor(clazz), getLanguageLevel());
    }
    else {
      String qName = notNullize(myReferenceElement.getQualifiedReferenceName(), "");
      return factory.createTypeByFQClassName(qName, myReferenceElement.getResolveScope());
    }
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return PsiNameHelper.getPresentableText(myReferenceElement.getReferenceName(), PsiAnnotation.EMPTY_ARRAY, myReferenceElement.getTypeArguments());
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return myReferenceElement.getCanonicalText();
  }

  @Override
  public boolean isValid() {
    return myReferenceElement.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.endsWith(getPresentableText()) /*optimization*/ && text.equals(getCanonicalText());
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myReferenceElement.getResolveScope();
  }

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  @NotNull
  public PsiClassType setLanguageLevel(@NotNull final LanguageLevel languageLevel) {
    return new GrClassReferenceType(myReferenceElement,languageLevel);
  }

  public GrReferenceElement getReference() {
    return myReferenceElement;
  }
}