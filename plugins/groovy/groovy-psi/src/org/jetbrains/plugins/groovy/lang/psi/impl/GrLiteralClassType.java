/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author peter
 */
public abstract class GrLiteralClassType extends PsiClassType {
  protected final GlobalSearchScope myScope;
  protected final JavaPsiFacade myFacade;
  private final GroovyPsiManager myGroovyPsiManager;

  public GrLiteralClassType(LanguageLevel languageLevel, @NotNull GlobalSearchScope scope, @NotNull JavaPsiFacade facade) {
    super(languageLevel);
    myScope = scope;
    myFacade = facade;
    myGroovyPsiManager = GroovyPsiManager.getInstance(myFacade.getProject());
  }

  @NotNull
  protected abstract String getJavaClassName();

  @Override
  @NotNull
  public ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      private final PsiClass myBaseClass = resolve();

      private final NotNullLazyValue<PsiSubstitutor> mySubstitutor = new NotNullLazyValue<PsiSubstitutor>() {
        @NotNull
        @Override
        protected PsiSubstitutor compute() {
          return inferSubstitutor(myBaseClass);
        }
      };

      @Override
      public PsiClass getElement() {
        return myBaseClass;
      }

      @Override
      @NotNull
      public PsiSubstitutor getSubstitutor() {
        return mySubstitutor.getValue();
      }

      @Override
      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      @Override
      public boolean isAccessible() {
        return true;
      }

      @Override
      public boolean isStaticsScopeCorrect() {
        return true;
      }

      @Override
      @Nullable
      public PsiElement getCurrentFileResolveScope() {
        return null;
      }

      @Override
      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  @NotNull
  private PsiSubstitutor inferSubstitutor(@Nullable PsiClass myBaseClass) {
    if (myBaseClass != null) {
      final PsiType[] typeArgs = getParameters();
      final PsiTypeParameter[] typeParams = myBaseClass.getTypeParameters();
      if (typeParams.length == typeArgs.length) {
        return PsiSubstitutor.EMPTY.putAll(myBaseClass, typeArgs);
      }
      else {
        return PsiSubstitutor.EMPTY.putAll(myBaseClass, createArray(typeParams.length));
      }
    }
    else {
      return PsiSubstitutor.EMPTY;
    }
  }

  @Override
  @NotNull
  public abstract String getClassName() ;

  @Override
  @NotNull
  public String getPresentableText() {
    String name = getClassName();
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    return name + "<" + StringUtil.join(params, new Function<PsiType, String>() {
      @Override
      public String fun(PsiType psiType) {
        return psiType.getPresentableText();
      }
    }, ", ") + ">";
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    String name = getJavaClassName();
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    final Function<PsiType, String> f = new Function<PsiType, String>() {
      @Override
      public String fun(PsiType psiType) {
        return psiType.getCanonicalText();
      }
    };
    return name + "<" + StringUtil.join(params, f, ", ") + ">";
  }

  @Override
  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @NotNull
  public GlobalSearchScope getScope() {
    return myScope;
  }

  @Override
  @Nullable
  public PsiClass resolve() {
    return myGroovyPsiManager.findClassWithCache(getJavaClassName(), getResolveScope());
  }

  @Override
  @NotNull
  public PsiClassType rawType() {
    return myGroovyPsiManager.createTypeByFQClassName(getJavaClassName(), myScope);
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return text.equals(getJavaClassName());
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return myScope;
  }

  protected static String getInternalCanonicalText(@Nullable PsiType type) {
    return type == null ? CommonClassNames.JAVA_LANG_OBJECT : type.getInternalCanonicalText();
  }

  @NotNull
  protected PsiType getLeastUpperBound(PsiType... psiTypes) {
    PsiType result = null;
    final PsiManager manager = getPsiManager();
    for (final PsiType other : psiTypes) {
      result = TypesUtil.getLeastUpperBoundNullable(result, other, manager);
    }
    return result == null ? LazyFqnClassType
      .getLazyType(CommonClassNames.JAVA_LANG_OBJECT, getLanguageLevel(), getResolveScope(), myFacade) : result;
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(myFacade.getProject());
  }
}
