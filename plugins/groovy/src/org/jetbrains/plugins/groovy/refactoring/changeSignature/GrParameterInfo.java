/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrParameterInfo implements JavaParameterInfo {
  private String myName;
  private final String myDefaultValue;
  private final String myDefaultInitializer;
  private final int myPosition;
  private CanonicalTypes.Type myTypeWrapper;

  public GrParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    final Project project = parameter.getProject();
    myName = parameter.getName();
    final PsiType type = parameter.getDeclaredType();
    if (type != null) {
      myTypeWrapper = CanonicalTypes.createTypeWrapper(type);
    }
    else {
      myTypeWrapper = null;
    }
    final GrExpression defaultInitializer = parameter.getDefaultInitializer();
    if (defaultInitializer != null) {
      myDefaultInitializer = defaultInitializer.getText();
    }
    else {
      myDefaultInitializer = "";
    }
    myDefaultValue = "";
  }

  public GrParameterInfo(@NotNull String name,
                         @Nullable String defaultValue,
                         @Nullable String defaultInitializer,
                         @Nullable PsiType type,
                         int position) {
    myName = name;
    myDefaultValue = defaultValue;
    myDefaultInitializer = defaultInitializer;
    myPosition = position;
    if (type != null) {
      myTypeWrapper = CanonicalTypes.createTypeWrapper(type);
    }
    else {
      myTypeWrapper = null;
    }
  }

  public String getName() {
    return myName;
  }

  public int getOldIndex() {
    return myPosition;
  }

  public String getDefaultValue() {
    return myDefaultValue;
  }

  @Nullable
  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    if (myTypeWrapper == null) return null;
    return myTypeWrapper.getType(context, manager);
  }

  public String getTypeText() {
    if (myTypeWrapper != null) {
      return myTypeWrapper.getTypeText();
    }
    return "";
  }

  @Nullable
  public CanonicalTypes.Type getTypeWrapper() {
    return myTypeWrapper;
  }

  public PsiExpression getValue(PsiCallExpression callExpression) {
    return JavaPsiFacade.getInstance(callExpression.getProject()).getElementFactory()
      .createExpressionFromText(myDefaultValue, callExpression);
  }

  public boolean isVarargType() {
    return getTypeText().endsWith("...") || getTypeText().endsWith("[]");
  }

  public boolean isUseAnySingleVariable() {
    return false;
  }

  public boolean isOptional() {
    return getDefaultInitializer().length() > 0;
  }

  public String getDefaultInitializer() {
    return myDefaultInitializer;
  }

  public boolean hasNoType() {
    return getTypeText().length() == 0;
  }

  /**
   * for testing only
   */
  public void setName(String newName) {
    myName = newName;
  }
}
