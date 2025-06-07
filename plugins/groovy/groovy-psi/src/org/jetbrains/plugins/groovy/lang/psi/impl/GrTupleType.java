// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.List;

public abstract class GrTupleType extends GrLiteralClassType {
  private final NotNullLazyValue<PsiType[]> myParameters = NotNullLazyValue.volatileLazy(() -> {
    List<PsiType> types = getComponentTypes();
    if (types.isEmpty()) return PsiType.EMPTY_ARRAY;
    final PsiType leastUpperBound = getLeastUpperBound(types.toArray(PsiType.EMPTY_ARRAY));
    if (leastUpperBound == PsiTypes.nullType()) return EMPTY_ARRAY;
    return new PsiType[]{leastUpperBound};
  });

  private final NotNullLazyValue<List<PsiType>> myComponents = NotNullLazyValue.volatileLazy(this::inferComponents);

  public GrTupleType(@NotNull GlobalSearchScope scope, @NotNull JavaPsiFacade facade) {
    this(scope, facade, LanguageLevel.JDK_1_5);
  }

  public GrTupleType(@NotNull GlobalSearchScope scope,
                     @NotNull JavaPsiFacade facade,
                     @NotNull LanguageLevel level) {
    super(level, scope, facade);
  }

  protected GrTupleType(@NotNull PsiElement context) {
    super(LanguageLevel.JDK_1_5, context);
  }

  @Override
  protected @NotNull String getJavaClassName() {
    return CommonClassNames.JAVA_UTIL_LIST;
  }

  @Override
  public @Nullable PsiType @NotNull [] getParameters() {
    return myParameters.getValue();
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
    List<PsiType> types = getComponentTypes();

    StringBuilder builder = new StringBuilder();
    builder.append("[");
    for (int i = 0; i < types.size(); i++) {
      if (i >= 2) {
        builder.append(",...");
        break;
      }

      if (i > 0) builder.append(", ");
      builder.append(getInternalCanonicalText(types.get(i)));
    }
    builder.append("]");
    return builder.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof GrTupleType) {
      List<PsiType> componentTypes = getComponentTypes();
      List<PsiType> otherComponents = ((GrTupleType)obj).getComponentTypes();
      return componentTypes.size() == otherComponents.size() &&
             componentTypes.equals(otherComponents);
    }
    return super.equals(obj);
  }

  @Override
  public boolean isAssignableFrom(@NotNull PsiType type) {
    if (type instanceof GrTupleType) {
      List<PsiType> otherComponents = ((GrTupleType)type).getComponentTypes();
      List<PsiType> componentTypes = getComponentTypes();
      for (int i = 0; i < Math.min(componentTypes.size(), otherComponents.size()); i++) {
        PsiType componentType = componentTypes.get(i);
        PsiType otherComponent = otherComponents.get(i);
        if (otherComponent == null) {
          if (componentType != null && !TypesUtil.isClassType(componentType, CommonClassNames.JAVA_LANG_OBJECT)) return false;
        }
        else if (componentType != null && !componentType.isAssignableFrom(otherComponent)) return false;
      }
      return true;
    }

    return super.isAssignableFrom(type);
  }

  public @NotNull List<PsiType> getComponentTypes() {
    return myComponents.getValue();
  }

  public final PsiType @NotNull [] getComponentTypesArray() {
    return getComponentTypes().toArray(PsiType.EMPTY_ARRAY);
  }

  protected abstract @NotNull List<PsiType> inferComponents();

  @Override
  public @NotNull PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new GrImmediateTupleType(getComponentTypes(), myFacade, getResolveScope());
  }
}
