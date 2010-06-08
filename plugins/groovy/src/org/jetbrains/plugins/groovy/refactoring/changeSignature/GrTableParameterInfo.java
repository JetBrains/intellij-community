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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCodeFragment;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Maxim.Medvedev
 */
public class GrTableParameterInfo {
  private GroovyCodeFragment myName;
  private GroovyCodeFragment myDefaultValue;
  private PsiTypeCodeFragment myType;
  private GroovyCodeFragment myDefaultInitializer;
  private final int myPosition;

  public GrTableParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    final Project project = parameter.getProject();
    myName = new GroovyCodeFragment(project, parameter.getName());
    final PsiType type = parameter.getDeclaredType();
    if (type != null) {
      String typeText = type.getCanonicalText();
      if (typeText == null) typeText = type.getPresentableText();
      myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment(typeText, parameter, true, true, true);
    }
    else {
      myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment("", parameter, true, true, true);
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

  public GrTableParameterInfo(Project project, PsiElement context) {
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

  public GroovyCodeFragment getDefaultInitializerFragment() {
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

  public GrParameterInfo generateParameterInfo() {
    String defaultInitializer = myDefaultInitializer.getText().trim();
    PsiType type;
    try {
      type = myType.getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      type = null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      type = null;
    }
    return new GrParameterInfo(getName(), getDefaultValue(), defaultInitializer, type, myPosition);
  }
}
