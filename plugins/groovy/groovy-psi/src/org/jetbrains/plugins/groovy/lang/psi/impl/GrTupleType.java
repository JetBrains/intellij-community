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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author ven
 */
public abstract class GrTupleType extends GrLiteralClassType {
  private final VolatileNotNullLazyValue<PsiType[]> myParameters = new VolatileNotNullLazyValue<PsiType[]>() {
    @NotNull
    @Override
    protected PsiType[] compute() {
      PsiType[] types = getComponentTypes();
      if (types.length == 0) return PsiType.EMPTY_ARRAY;
      final PsiType leastUpperBound = getLeastUpperBound(types);
      if (leastUpperBound == PsiType.NULL) return EMPTY_ARRAY;
      return new PsiType[]{leastUpperBound};
    }
  };

  private final VolatileNotNullLazyValue<PsiType[]> myComponents = new VolatileNotNullLazyValue<PsiType[]>() {
    @NotNull
    @Override
    protected PsiType[] compute() {
      return inferComponents();
    }
  };

  public GrTupleType(@NotNull GlobalSearchScope scope, @NotNull JavaPsiFacade facade) {
    this(scope, facade, LanguageLevel.JDK_1_5);
  }

  public GrTupleType(@NotNull GlobalSearchScope scope,
                     @NotNull JavaPsiFacade facade,
                     @NotNull LanguageLevel level) {
    super(level, scope, facade);
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    return CommonClassNames.JAVA_UTIL_ARRAY_LIST;
  }

  @Override
  @NotNull
  public String getClassName() {
    return StringUtil.getShortName(getJavaClassName());
  }

  @Override
  @NotNull
  public PsiType[] getParameters() {
    return myParameters.getValue();
  }

  @Override
  @NotNull
  public String getInternalCanonicalText() {
    PsiType[] types = getComponentTypes();

    StringBuilder builder = new StringBuilder();
    builder.append("[");
    for (int i = 0; i < types.length; i++) {
      if (i >= 2) {
        builder.append(",...");
        break;
      }

      if (i > 0) builder.append(", ");
      builder.append(getInternalCanonicalText(types[i]));
    }
    builder.append("]");
    return builder.toString();
  }

  public boolean equals(Object obj) {
    if (obj instanceof GrTupleType) {
      PsiType[] componentTypes = getComponentTypes();
      PsiType[] otherComponents = ((GrTupleType)obj).getComponentTypes();
      for (int i = 0; i < Math.min(componentTypes.length, otherComponents.length); i++) {
        if (!Comparing.equal(componentTypes[i], otherComponents[i])) return false;
      }
      return true;
    }
    return super.equals(obj);
  }

  @Override
  public boolean isAssignableFrom(@NotNull PsiType type) {
    if (type instanceof GrTupleType) {
      PsiType[] otherComponents = ((GrTupleType)type).getComponentTypes();
      PsiType[] componentTypes = getComponentTypes();
      for (int i = 0; i < Math.min(componentTypes.length, otherComponents.length); i++) {
        PsiType componentType = componentTypes[i];
        PsiType otherComponent = otherComponents[i];
        if (otherComponent == null) {
          if (componentType != null && !TypesUtil.isClassType(componentType, CommonClassNames.JAVA_LANG_OBJECT)) return false;
        }
        else if (componentType != null && !componentType.isAssignableFrom(otherComponent)) return false;
      }
      return true;
    }

    return super.isAssignableFrom(type);
  }

  @NotNull
  public PsiType[] getComponentTypes() {
    return myComponents.getValue();
  }

  @NotNull
  protected abstract  PsiType[] inferComponents();

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new GrImmediateTupleType(getComponentTypes(), myFacade, getResolveScope());
  }
}
