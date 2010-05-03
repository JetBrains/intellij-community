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
import com.intellij.psi.search.GlobalSearchScope;
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
  private CanonicalTypes.Type myTypeWrapper;
  private boolean myWasOptional = false;
  private boolean myWasVarArg = false;

  public GrParameterInfo(GrParameter parameter, int position) {
    myPosition = position;
    final Project project = parameter.getProject();
    myName = new GroovyCodeFragment(project, parameter.getName());
    final PsiType type = parameter.getDeclaredType();
    if (type != null) {
      myType = JavaPsiFacade.getElementFactory(project).createTypeCodeFragment(type.getCanonicalText(), parameter, true, true);
      myWasVarArg = myType instanceof PsiArrayType;
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
    myWasOptional = parameter.isOptional();
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

  @Nullable
  public PsiType createType(PsiElement context, final PsiManager manager) throws IncorrectOperationException {
    try {
      return myType.getType();
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return null;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return JavaPsiFacade.getElementFactory(manager.getProject())
        .createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(manager.getProject()));
    }
  }


  public String getTypeText() {
    CanonicalTypes.Type type = getTypeWrapper();
    if (type != null) {
      return type.getTypeText();
    }
    return "";
  }

  @Nullable
  public CanonicalTypes.Type getTypeWrapper() {
    if (myTypeWrapper == null) {
      PsiType type = createType(myType.getContext(), myType.getManager());
      if (type != null) {
        myTypeWrapper = CanonicalTypes.createTypeWrapper(type);
      }
    }
    return myTypeWrapper;
  }

  public PsiExpression getValue(PsiCallExpression callExpression) {
    return JavaPsiFacade.getInstance(callExpression.getProject()).getElementFactory()
      .createExpressionFromText(myDefaultValue.getText(), callExpression);
  }

  public boolean isVarargType() {
    return getTypeText().endsWith("...") || getTypeText().endsWith("[]");
  }

  public boolean wasOptional() {
    return myWasOptional;
  }

  public boolean isOptional() {
    return getDefaultInitializer().length() > 0;
  }

  public String getDefaultInitializer() {
    return myDefaultInitializer.getText().trim();
  }

  public boolean wasWasVarArg() {
    return myWasVarArg;
  }

  public boolean hasNoType() {
    return myType.getText().trim().length() == 0;
  }
}
