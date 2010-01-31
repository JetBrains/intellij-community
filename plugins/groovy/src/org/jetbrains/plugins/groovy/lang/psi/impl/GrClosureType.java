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

import com.intellij.openapi.util.Comparing;
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
  private final GlobalSearchScope myScope;
  @Nullable
  private final PsiType myReturnType;
  private final PsiType[] myParameterTypes;
  private final boolean[] myOptionals;
  private final PsiManager myManager;

  private GrClosureType(GlobalSearchScope scope,
                        @Nullable PsiType returnType,
                        PsiType[] parameters,
                        boolean[] optionals,
                        PsiManager manager, LanguageLevel languageLevel) {
    super(languageLevel);
    myScope = scope;
    myReturnType = returnType;
    myParameterTypes = parameters;
    myOptionals = optionals;
    myManager = manager;
  }

  @Nullable
  public PsiClass resolve() {
    return JavaPsiFacade.getInstance(myManager.getProject()).findClass(GrClosableBlock.GROOVY_LANG_CLOSURE, getResolveScope());
  }

  @Nullable
  public PsiType getClosureReturnType() {
    return myReturnType;
  }

  public PsiType[] getClosureParameterTypes() {
    return myParameterTypes;
  }

  public boolean isOptionalParameter(int parameterIndex) {
    return myOptionals[parameterIndex];
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
    if (myParameterTypes.length > 0 && !myParameterTypes[0].isValid()) return false;
    return myReturnType == null || myReturnType.isValid();
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrClosureType) {
      GrClosureType other = (GrClosureType)obj;
      if (!Comparing.equal(myReturnType, other.myReturnType)) return false;
      if (myParameterTypes.length != other.myParameterTypes.length) return false;
      for (int i = 0; i < myParameterTypes.length; i++) {
        if (myOptionals[i] != other.myOptionals[i]) return false;
        if (!other.myParameterTypes[i].equals(myParameterTypes[i])) return false;
      }
      return true;
    }

    return super.equals(obj);
  }

  public boolean isAssignableFrom(@NotNull PsiType type) {
    if (type instanceof GrClosureType) {
      GrClosureType other = (GrClosureType)type;
      if (myReturnType == null || other.myReturnType == null) {
        return myReturnType == null && other.myReturnType == null;
      }
      if (!myReturnType.isAssignableFrom(other.myReturnType)) return false;
      if (myParameterTypes.length != other.myParameterTypes.length) return false;
      for (int i = 0; i < myParameterTypes.length; i++) {
        if (myOptionals[i] != other.myOptionals[i]) return false;
        if (!other.myParameterTypes[i].isAssignableFrom(myParameterTypes[i])) return false;
      }
      return true;
    }
    return super.isAssignableFrom(type);
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
    return create(myReturnType, myParameterTypes, myOptionals, myManager, myScope, languageLevel);
  }

  public static GrClosureType create(GrClosableBlock closure) {
    return create(closure.getResolveScope(), closure.getReturnType(), closure.getAllParameters(), closure.getManager());
  }

  public static GrClosureType create(GlobalSearchScope scope, PsiType returnType, PsiParameter[] parameters, PsiManager manager) {
    PsiType[] parameterTypes = new PsiType[parameters.length];
    boolean[] optionals = new boolean[parameters.length];
    for (int i = 0; i < optionals.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter instanceof GrParameter) {
        optionals[i] = ((GrParameter)parameter).isOptional();
      } else if (i == 0) { // for implicit "it" parameter
        optionals[i] = true;
      } else {
        optionals[i] = false;
      }
      parameterTypes[i] = parameter.getType();
    }
    return create(returnType, parameterTypes, optionals, manager, scope, LanguageLevel.JDK_1_5);
  }

  public static GrClosureType create(PsiType returnType,
                                     PsiType[] parameterTypes,
                                     boolean[] optionals,
                                     PsiManager manager,
                                     GlobalSearchScope scope, LanguageLevel languageLevel) {
    return new GrClosureType(scope, returnType, parameterTypes, optionals, manager,languageLevel);
  }

  @Nullable
  public PsiType curry(int num) {
    if (num > myParameterTypes.length) return null;
    PsiType[] newParameterTypes = new PsiType[myParameterTypes.length - num];
    boolean[] newOptionals = new boolean[myParameterTypes.length - num];
    System.arraycopy(myParameterTypes, num, newParameterTypes, 0, newParameterTypes.length);
    System.arraycopy(myOptionals, num, newOptionals, 0, newOptionals.length);
    return create(myReturnType, newParameterTypes, newOptionals, myManager, myScope, myLanguageLevel);
  }
}
