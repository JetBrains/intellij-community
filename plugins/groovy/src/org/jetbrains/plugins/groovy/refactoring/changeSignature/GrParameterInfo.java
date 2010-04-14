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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrParameterInfo implements JavaParameterInfo {
  private GroovyCodeFragment myName;
  private GroovyCodeFragment myDefaultValue;
  private PsiTypeCodeFragment myType;
  private GroovyCodeFragment myDefaultInitializer;
  private final int myPosition;

  public GrParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    final Project project = parameter.getProject();
    myName = new GroovyCodeFragment(project, parameter.getName());
    final PsiType type = parameter.getDeclaredType();
    if (type != null) {
      myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment(type.getCanonicalText(), parameter, true, true);
    }
    else {
      myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment("", parameter, true, true);
    }
    final GrExpression defaultInitializer = parameter.getDefaultInitializer();
    if (defaultInitializer != null) {
      myDefaultInitializer = new GroovyCodeFragment(project, defaultInitializer.getText());
    }
    else {
      myDefaultInitializer = new GroovyCodeFragment(project, "");
    }
    myDefaultValue = new GroovyCodeFragment(project, "");
  }

  public GrParameterInfo(Project project, PsiElement context) {
    this.myPosition = -1;
    myName = new GroovyCodeFragment(project, "");
    myDefaultValue = new GroovyCodeFragment(project, "");
    myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment("", context, true, true);
    myDefaultInitializer = new GroovyCodeFragment(project, "");
  }

  public GroovyCodeFragment getNameFragment() {
    return myName;
  }

  public GroovyCodeFragment getDefaultValueFragment() {
    return myDefaultValue;
  }

  public PsiTypeCodeFragment getTypeFragment() {
    return myType;
  }

  public GroovyCodeFragment getDefaultInitializer() {
    return myDefaultInitializer;
  }

  public String getName() {
    return myName.getText().trim();
  }

  public int getOldIndex() {
    return myPosition;
  }

  public String getDefaultValue() {
    return myDefaultValue.getText().trim();
  }

  @Nullable
  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    try {
      return myType.getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return null;
    }
  }


  public String getTypeText() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public CanonicalTypes.Type getTypeWrapper() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiExpression getValue(PsiCallExpression callExpression) {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
