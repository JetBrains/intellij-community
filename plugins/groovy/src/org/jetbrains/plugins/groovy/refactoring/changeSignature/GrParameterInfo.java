// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrParameterInfo implements JavaParameterInfo {
  private @NotNull String myName;
  private @NotNull String myDefaultValue = "";
  private @NotNull String myDefaultInitializer = "";
  private final int myPosition;
  private @Nullable CanonicalTypes.Type myTypeWrapper;
  private boolean myUseAnySingleVariable;

  public GrParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    myName = parameter.getName();
    final PsiType type = parameter.getDeclaredType();
    if (type != null) {
      myTypeWrapper = CanonicalTypes.createTypeWrapper(type);
    }
    else if (parameter.hasModifierProperty(GrModifier.DEF)) {
      myTypeWrapper = CanonicalTypes.createTypeWrapper(JavaPsiFacade.getElementFactory(parameter.getProject()).createTypeFromText("def", null));
    }
    else {
      myTypeWrapper = null;
    }
    final GrExpression defaultInitializer = parameter.getInitializerGroovy();
    if (defaultInitializer != null) setInitializer(defaultInitializer.getText());
    myUseAnySingleVariable = false;
  }

  public GrParameterInfo(@NotNull String name,
                         @Nullable String defaultValue,
                         @Nullable String defaultInitializer,
                         @Nullable PsiType type,
                         int position,
                         boolean useAnySingleVariable) {
    myName = name;
    myPosition = position;
    myUseAnySingleVariable = useAnySingleVariable;
    setType(type);
    setDefaultValue(defaultValue);
    setInitializer(defaultInitializer);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public int getOldIndex() {
    return myPosition;
  }

  @Override
  public @NotNull String getDefaultValue() {
    return forceOptional() ? getDefaultInitializer() : myDefaultValue;
  }

  @Override
  public @Nullable PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    return myTypeWrapper != null ? myTypeWrapper.getType(context, manager) : null;
  }

  @Override
  public @NotNull String getTypeText() {
    return myTypeWrapper != null ? myTypeWrapper.getTypeText() : "";
  }

  @Override
  public @Nullable CanonicalTypes.Type getTypeWrapper() {
    return myTypeWrapper;
  }

  @Override
  public PsiExpression getValue(PsiCallExpression callExpression) {
    return JavaPsiFacade.getElementFactory(callExpression.getProject()).createExpressionFromText(getDefaultValue(), callExpression);
  }

  @Override
  public boolean isVarargType() {
    return getTypeText().endsWith("...") || getTypeText().endsWith("[]");
  }

  @Override
  public boolean isUseAnySingleVariable() {
    return myUseAnySingleVariable;
  }

  @Override
  public void setUseAnySingleVariable(boolean useAnyVar) {
    myUseAnySingleVariable = useAnyVar;
  }

  public boolean isOptional() {
    return !getDefaultInitializer().isEmpty();
  }

  public @NotNull String getDefaultInitializer() {
    return myDefaultInitializer;
  }

  public boolean hasNoType() {
    return getTypeText().isEmpty();
  }

  public boolean forceOptional() {
    return myPosition < 0 && StringUtil.isEmpty(myDefaultValue);
  }

  /**
   * for testing only
   */
  @Override
  public void setName(String newName) {
    myName = StringUtil.notNullize(newName);
  }

  @Override
  public void setType(@Nullable PsiType type) {
    myTypeWrapper = type == null ? null : CanonicalTypes.createTypeWrapper(type);
  }

  public void setInitializer(@Nullable String initializer) {
    myDefaultInitializer = StringUtil.notNullize(initializer);
  }

  public void setDefaultValue(@Nullable String defaultValue) {
    myDefaultValue = StringUtil.notNullize(defaultValue);
  }
}
