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
  @NotNull private String myName;
  @NotNull private String myDefaultValue;
  @NotNull private String myDefaultInitializer;
  private final int myPosition;
  @Nullable private CanonicalTypes.Type myTypeWrapper;
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
    if (defaultInitializer != null) {
      myDefaultInitializer = defaultInitializer.getText();
    }
    else {
      myDefaultInitializer = "";
    }
    myDefaultValue = "";
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
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public int getOldIndex() {
    return myPosition;
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return forceOptional() ? getDefaultInitializer() : myDefaultValue;
  }

  @Override
  @Nullable
  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    return myTypeWrapper != null ? myTypeWrapper.getType(context, manager) : null;
  }

  @Override
  @NotNull
  public String getTypeText() {
    return myTypeWrapper != null ? myTypeWrapper.getTypeText() : "";
  }

  @Override
  @Nullable
  public CanonicalTypes.Type getTypeWrapper() {
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

  @NotNull
  public String getDefaultInitializer() {
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
  public void setName(@NotNull String newName) {
    myName = newName;
  }

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
