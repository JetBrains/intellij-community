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

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragmentFactory;
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
  private final GroovyCodeFragment myName;
  private final GroovyCodeFragment myDefaultValue;
  private final PsiTypeCodeFragment myType;
  private final GroovyCodeFragment myDefaultInitializer;
  private final int myPosition;
  private boolean myUseAnyVar = false;

  public GrTableParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    final Project project = parameter.getProject();
    myName = new GroovyCodeFragment(project, parameter.getName());
    final PsiType type = parameter.getDeclaredType();
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    if (type != null) {
      String typeText = type.getCanonicalText();
      if (typeText == null) typeText = type.getPresentableText();
      myType = factory.createTypeCodeFragment(typeText, parameter, true, JavaCodeFragmentFactory.ALLOW_VOID | JavaCodeFragmentFactory.ALLOW_ELLIPSIS);
    }
    else {
      myType = factory.createTypeCodeFragment("", parameter, true, JavaCodeFragmentFactory.ALLOW_VOID | JavaCodeFragmentFactory.ALLOW_ELLIPSIS);
    }
    final GrExpression defaultInitializer = parameter.getInitializerGroovy();
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
    myType = JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment("", context, true, JavaCodeFragmentFactory.ALLOW_VOID);
    myDefaultInitializer = new GroovyCodeFragment(project, "");
  }

  public GrTableParameterInfo(Project project,
                              PsiElement context,
                              String name,
                              String type,
                              String defaultValue,
                              String defaultInitializer) {
    this.myPosition = -1;
    myName = new GroovyCodeFragment(project, name);
    myDefaultValue = new GroovyCodeFragment(project, defaultValue);
    myType = JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment(type, context, true, JavaCodeFragmentFactory.ALLOW_VOID);
    myDefaultInitializer = new GroovyCodeFragment(project, defaultInitializer);
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
    String defaultValue = getDefaultValue();
    return new GrParameterInfo(getName(), defaultValue, defaultInitializer, type, myPosition, myUseAnyVar);
  }

  public boolean isUseAnyVar() {
    return myUseAnyVar;
  }

  public void setUseAnyVar(boolean useAnyVar) {
    myUseAnyVar = useAnyVar;
  }
}
