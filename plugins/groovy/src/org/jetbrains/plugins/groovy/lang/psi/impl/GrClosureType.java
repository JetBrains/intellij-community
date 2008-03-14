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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

/**
 * @author ven
*/
public class GrClosureType extends PsiClassType {
  private GlobalSearchScope myScope;
  private PsiType myReturnType;
  private PsiParameter[] myParameters;
  private PsiManager myManager;

  private GrClosureType(GlobalSearchScope scope, PsiType returnType, PsiParameter[] parameters, PsiManager manager) {
    myScope = scope;
    myReturnType = returnType;
    myParameters = parameters;
    myManager = manager;
    myLanguageLevel = LanguageLevel.JDK_1_5;
  }

  @Nullable
  public PsiClass resolve() {
    return myManager.findClass(GrClosableBlock.GROOVY_LANG_CLOSURE, getResolveScope());
  }

  public PsiType getClosureReturnType() {
    return myReturnType;
  }

  public PsiParameter[] getClosureParameters() {
    return myParameters;
  }

  public String getClassName() {
    return "Closure";
  }

  @NotNull
  public PsiType[] getParameters() {
    //todo
    return PsiType.EMPTY_ARRAY;
  }

  @NotNull
  public ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      public PsiClass getElement() {
        return resolve();
      }

      public PsiSubstitutor getSubstitutor() {
        return PsiSubstitutor.UNKNOWN;
      }

      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      public boolean isAccessible() {
        return true;
      }

      public boolean isStaticsScopeCorrect() {
        return true;
      }

      public PsiElement getCurrentFileResolveScope() {
        return null;
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
    return "Closure";
  }

  @Nullable
  public String getCanonicalText() {
    PsiClass resolved = resolve();
    if (resolved == null) return null;
    return resolved.getQualifiedName();
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  public boolean isValid() {
    if (myParameters.length > 0 && !myParameters[0].isValid()) return false;
    return myReturnType.isValid();
  }

  public boolean equalsToText(@NonNls String text) {
    return text.equals(GrClosableBlock.GROOVY_LANG_CLOSURE);
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myScope;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    GrClosureType copy = new GrClosureType(myScope, myReturnType, myParameters, myManager);
    copy.myLanguageLevel = languageLevel;
    return copy;
  }

  public static GrClosureType create(GrClosableBlock closure) {
    return new GrClosureType(closure.getResolveScope(), closure.getReturnType(), closure.getParameters(), closure.getManager());
  }

  public static GrClosureType create (GlobalSearchScope scope, PsiType returnType, PsiParameter[] parameters, PsiManager manager) {
    return new GrClosureType(scope, returnType, parameters, manager);
  }
}