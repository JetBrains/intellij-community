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
public class GrParameterInfo {
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
    final GrExpression defaultInitializer = parameter.getDefaultInitializer();
    if (defaultInitializer != null) {
      myDefaultInitializer = new GroovyCodeFragment(project, defaultInitializer.getText());
    }
  }

  public GrParameterInfo(Project project, PsiElement context) {
    this.myPosition = -1;
    myName = new GroovyCodeFragment(project, "");
    myDefaultValue = new GroovyCodeFragment(project, "");
    myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment("", context, true, true);
    myDefaultInitializer = new GroovyCodeFragment(project, "");
  }

  public GroovyCodeFragment getName() {
    return myName;
  }

  public GroovyCodeFragment getDefaultValue() {
    return myDefaultValue;
  }

  public PsiTypeCodeFragment getType() {
    return myType;
  }

  public GroovyCodeFragment getDefaultInitializer() {
    return myDefaultInitializer;
  }

  public int getPosition() {
    return myPosition;
  }
}
