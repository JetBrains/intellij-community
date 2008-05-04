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

/**
 * @author ven
*/
public class GrTupleType extends PsiClassType {
  private GlobalSearchScope myScope;
  private PsiManager myManager;
  private PsiType[] myComponentTypes;
  @NonNls
  private static final String JAVA_UTIL_LIST = "java.util.List";


  public GrTupleType(PsiType[] componentTypes, PsiManager manager, GlobalSearchScope scope) {
    myComponentTypes = componentTypes;
    myManager = manager;
    myScope = scope;
  }

  @Nullable
  public PsiClass resolve() {
    return myManager.findClass(JAVA_UTIL_LIST, getResolveScope());
  }

  public String getClassName() {
    return "List";
  }

  @NotNull
  public PsiType[] getParameters() {
    if (myComponentTypes.length == 0) return PsiType.EMPTY_ARRAY;
    PsiType result = myComponentTypes[0];
    for (int i = 1; i < myComponentTypes.length; i++) {
      final PsiType other = myComponentTypes[i];
      if (other == null) continue;
      if (result == null) result = other;
      if (result.isAssignableFrom(other)) continue;
      if (other.isAssignableFrom(result)) result = other;
      result = GenericsUtil.getLeastUpperBound(result, other, myManager);
    }

    return new PsiType[]{result};
  }

  @NotNull
  public ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      private PsiClass myListClass = resolve();

      public PsiClass getElement() {
        return myListClass;
      }

      public PsiSubstitutor getSubstitutor() {
        PsiSubstitutor result = PsiSubstitutor.EMPTY;
        PsiType[] typeArgs = getParameters();
        if (myListClass != null && myListClass.getTypeParameters().length == 1 && typeArgs.length == 1) {
          result = result.put(myListClass.getTypeParameters()[0], typeArgs[0]);
        }
        return result;
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
    return myManager.getElementFactory().createTypeByFQClassName(JAVA_UTIL_LIST, myScope);
  }

  public String getPresentableText() {
    return "List";
  }

  @Nullable
  public String getCanonicalText() {
    PsiClass resolved = resolve();
    if (resolved == null) return null;
    return resolved.getQualifiedName();
  }

  public String getInternalCanonicalText() {
    StringBuilder builder = new StringBuilder();
    builder.append("[");
    for (int i = 0; i < myComponentTypes.length; i++) {
      if (i > 0) builder.append(", ");
      PsiType type = myComponentTypes[i];
      @NonNls String componentText = type == null ? "java.lang.Object" : type.getInternalCanonicalText();
      builder.append(componentText);
    }
    builder.append("]");
    return builder.toString();
  }

  public boolean isValid() {
    for (PsiType initializer : myComponentTypes) {
      if (initializer != null && !initializer.isValid()) return false;
    }
    return true;
  }

  public boolean equalsToText(@NonNls String text) {
    return text.equals(JAVA_UTIL_LIST);
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
    GrTupleType copy = new GrTupleType(myComponentTypes, myManager, myScope);
    copy.myLanguageLevel = languageLevel;
    return copy;
  }

  public boolean isAssignableFrom(@NotNull PsiType type) {
    if (type instanceof GrTupleType) {
      PsiType[] otherComponents = ((GrTupleType) type).myComponentTypes;
      for (int i = 0; i < Math.min(myComponentTypes.length, otherComponents.length); i++) {
        PsiType componentType = myComponentTypes[i];
        PsiType otherComponent = otherComponents[i];
        if (otherComponent == null) {
          if (componentType != null && !componentType.equalsToText("java.lang.Object")) return false;
        }
        else if (componentType != null && !componentType.isAssignableFrom(otherComponent)) return false;
      }
      return true;
    }

    return super.isAssignableFrom(type);
  }
}