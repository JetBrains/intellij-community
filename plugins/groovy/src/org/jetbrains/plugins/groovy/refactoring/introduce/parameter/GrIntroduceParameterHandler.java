/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.parameter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceRefactoringError;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterHandler extends GrIntroduceHandlerBase<GrIntroduceParameterSettings> {

  @Override
  protected String getRefactoringName() {
    return RefactoringBundle.message("introduce.parameter.title");
  }

  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_PARAMETER;
  }

  @NotNull
  @Override
  protected PsiElement findScope(GrExpression expression, GrVariable variable) {
    @NotNull PsiElement place = expression == null ? variable : expression;

    final PsiMethod method = PsiTreeUtil.getParentOfType(place, PsiMethod.class, true, PsiClass.class);
    if (method == null) throw new GrIntroduceRefactoringError(GroovyRefactoringBundle.message("there.is.no.method"));
    return method;
  }

  @Override
  public GrIntroduceContext getContext(Project project, Editor editor, GrExpression expression, @Nullable GrVariable variable) {
    final GrIntroduceContext context = super.getContext(project, editor, expression, variable);

    assert context.scope instanceof GrMethod;
    GrMethod curMethod = (GrMethod)context.scope;

    final PsiMethod methodToSearchFor = IntroduceParameterHandler.chooseEnclosingMethod(curMethod);

    return new GrIntroduceParameterContext(context, curMethod, methodToSearchFor);
  }

  @Override
  protected void checkExpression(GrExpression selectedExpr) throws GrIntroduceRefactoringError {
    //nothing to do
  }

  @Override
  protected void checkVariable(GrVariable variable) throws GrIntroduceRefactoringError {
    //nothing to do
  }

  @Override
  protected void checkOccurrences(PsiElement[] occurrences) {
    //nothing to do
  }

  @Override
  protected GrIntroduceDialog<GrIntroduceParameterSettings> getDialog(GrIntroduceContext context) {
    assert context instanceof GrIntroduceParameterContext;
    TObjectIntHashMap<GrParameter> toRemove = GroovyIntroduceParameterUtil.findParametersToRemove(context);
    return new GrIntroduceParameterDialog((GrIntroduceParameterContext)context, toRemove);
  }

  @Override
  public GrVariable runRefactoring(GrIntroduceContext context, GrIntroduceParameterSettings settings) {
    return null;
  }
}
