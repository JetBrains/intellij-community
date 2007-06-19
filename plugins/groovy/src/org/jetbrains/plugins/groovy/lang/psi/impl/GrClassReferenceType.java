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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;

/**
 * @author ven
*/
public class GrClassReferenceType extends PsiClassType {
  private GrTypeOrPackageReferenceElement myReferenceElement;

  public GrClassReferenceType(GrTypeOrPackageReferenceElement referenceElement) {
    myReferenceElement = referenceElement;
    myLanguageLevel = LanguageLevel.JDK_1_5;
  }

  @Nullable
  public PsiClass resolve() {
    PsiElement resolved = myReferenceElement.resolve();
    return resolved instanceof PsiClass ? (PsiClass) resolved :
        resolved instanceof PsiMethod ? //constructor
            ((PsiMethod) resolved).getContainingClass() : null;
  }

  public String getClassName() {
    return myReferenceElement.getReferenceName();
  }

  @NotNull
  public PsiType[] getParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @NotNull
    public ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      public PsiClass getElement() {
        return resolve();
      }

      public PsiSubstitutor getSubstitutor() {
        return PsiSubstitutor.EMPTY;
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      public boolean isAccessible() {
        return true; //TODO
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
    return this;
  }

  public String getPresentableText() {
    return myReferenceElement.getReferenceName();
  }

  @Nullable
  public String getCanonicalText() {
    PsiClass resolved = resolve();
    return resolved == null ? null : resolved.getQualifiedName();
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
    GrClassReferenceType copy = new GrClassReferenceType(myReferenceElement);
    copy.myLanguageLevel = languageLevel;
    return copy;
  }
}
