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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author ven
*/
public class GrClosureType extends PsiClassType {
  private GrClosableBlock myClosure;

  private GrClosureType(GrClosableBlock closure) {
    myClosure = closure;
    myLanguageLevel = LanguageLevel.JDK_1_5;
  }

  @Nullable
  public PsiClass resolve() {
    return myClosure.getManager().findClass(GrClosableBlock.GROOVY_LANG_CLOSURE, getResolveScope());
  }

  public PsiType getClosureReturnType() {
    return myClosure.getReturnType();
  }

  public PsiType[] getClosureParameterTypes() {
    final GrParameter[] params = myClosure.getParameters();
    PsiType[] result = new PsiType[params.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = params[i].getType();
    }
    return result;
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
    return myClosure.isValid();
  }

  public boolean equalsToText(@NonNls String text) {
    return text.equals(GrClosableBlock.GROOVY_LANG_CLOSURE);
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myClosure.getResolveScope();
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    GrClosureType copy = create(myClosure);
    copy.myLanguageLevel = languageLevel;
    return copy;
  }

  public static GrClosureType create(GrClosableBlock closure) {
    return new GrClosureType(closure);
  }
}