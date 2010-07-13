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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class GrTupleType extends GrLiteralClassType {
  private static final PsiType[] RAW_PARAMETERS = new PsiType[]{null};
  private final PsiType[] myComponentTypes;

  public GrTupleType(PsiType[] componentTypes, JavaPsiFacade facade, GlobalSearchScope scope) {
    this(componentTypes, facade, scope,LanguageLevel.JDK_1_5);
  }
  public GrTupleType(PsiType[] componentTypes, JavaPsiFacade facade, GlobalSearchScope scope,LanguageLevel languageLevel) {
    super(languageLevel, scope, facade);
    myComponentTypes = componentTypes;
  }

  @Override
  protected String getJavaClassName() {
    return CommonClassNames.JAVA_UTIL_LIST;
  }

  public String getClassName() {
    return "List";
  }

  @NotNull
  public PsiType[] getParameters() {
    if (myComponentTypes.length == 0) return RAW_PARAMETERS;
    return new PsiType[]{getLeastUpperBound(myComponentTypes)};
  }

  public String getInternalCanonicalText() {
    StringBuilder builder = new StringBuilder();
    builder.append("[");
    for (int i = 0; i < myComponentTypes.length; i++) {
      if (i >= 2) {
        builder.append(",...");
        break;
      }

      if (i > 0) builder.append(", ");
      builder.append(getInternalCanonicalText(myComponentTypes[i]));
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

  public PsiClassType setLanguageLevel(final LanguageLevel languageLevel) {
    return new GrTupleType(myComponentTypes, myFacade, myScope,languageLevel);
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrTupleType) {
      PsiType[] otherComponents = ((GrTupleType) obj).myComponentTypes;
      for (int i = 0; i < Math.min(myComponentTypes.length, otherComponents.length); i++) {
        if (!Comparing.equal(myComponentTypes[i], otherComponents[i])) return false;
      }
      return true;
    }
    return super.equals(obj);
  }

  public boolean isAssignableFrom(@NotNull PsiType type) {
    if (type instanceof GrTupleType) {
      PsiType[] otherComponents = ((GrTupleType) type).myComponentTypes;
      for (int i = 0; i < Math.min(myComponentTypes.length, otherComponents.length); i++) {
        PsiType componentType = myComponentTypes[i];
        PsiType otherComponent = otherComponents[i];
        if (otherComponent == null) {
          if (componentType != null && !componentType.equalsToText("java.lang.Object")) return false;
        } else if (componentType != null && !componentType.isAssignableFrom(otherComponent)) return false;
      }
      return true;
    }

    return super.isAssignableFrom(type);
  }

  public PsiType[] getComponentTypes() {
    return myComponentTypes;
  }

}
