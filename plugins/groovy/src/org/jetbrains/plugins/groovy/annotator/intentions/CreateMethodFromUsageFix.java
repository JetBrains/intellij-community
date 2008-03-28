/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.editor.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.editor.template.expressions.ParameterNameExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class CreateMethodFromUsageFix implements IntentionAction {
  private GrMemberOwner myTargetClass;
  private GrReferenceExpression myRefExpression;

  public CreateMethodFromUsageFix(GrReferenceExpression refExpression, GrMemberOwner targetClass) {
    myRefExpression = refExpression;
    myTargetClass = targetClass;
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("create.method.from.usage", myRefExpression.getReferenceName());
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myTargetClass.isValid() && myRefExpression.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    StringBuffer methodBuffer = new StringBuffer();
    if (PsiUtil.isInStaticContext(myRefExpression, myTargetClass)) methodBuffer.append("static ");
    methodBuffer.append("Object ").append(myRefExpression.getReferenceName()).append(" (");
    PsiType[] argTypes = PsiUtil.getArgumentTypes(myRefExpression, false, false);
    assert argTypes != null;
    ChooseTypeExpression[] paramTypesExpressions = new ChooseTypeExpression[argTypes.length];
    ParameterNameExpression paramNameExpression = new ParameterNameExpression();
    for (int i = 0; i < argTypes.length; i++) {
      PsiType argType = argTypes[i];
      if (argType == null) argType = TypesUtil.getJavaLangObject(myRefExpression);
      if (i > 0) methodBuffer.append(", ");
      methodBuffer.append("Object o").append(i);
      paramTypesExpressions[i] = new ChooseTypeExpression(new TypeConstraint[]{SupertypeConstraint.create(argType)}, myRefExpression.getManager());
    }
    methodBuffer.append(") {\n}");


    GrMethod method = GroovyPsiElementFactory.getInstance(project).createMethodFromText(methodBuffer.toString());
    method = myTargetClass.addMemberDeclaration(method, null);
    GrTypeElement typeElement = method.getReturnTypeElementGroovy();
    assert typeElement != null;
    TypeConstraint[] constraints = GroovyExpectedTypesUtil.calculateTypeConstraints((GrCallExpression) myRefExpression.getParent());
    ChooseTypeExpression expr = new ChooseTypeExpression(constraints, PsiManager.getInstance(project));
    TemplateBuilder builder = new TemplateBuilder(method);
    builder.replaceElement(typeElement, expr);
    GrParameter[] parameters = method.getParameterList().getParameters();
    assert parameters.length == argTypes.length;
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      GrTypeElement parameterTypeElement = parameter.getTypeElementGroovy();
      builder.replaceElement(parameterTypeElement, paramTypesExpressions[i]);
      builder.replaceElement(parameter.getNameIdentifierGroovy(), paramNameExpression);
    }
    GrOpenBlock body = method.getBlock();
    assert body != null;
    PsiElement lbrace = body.getLBrace();
    assert lbrace != null;
    builder.setEndVariableAfter(lbrace);

    method = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(method);
    Template template = builder.buildTemplate();

    Editor newEditor = QuickfixUtil.positionCursor(project, myTargetClass.getContainingFile(), method);
    TextRange range = method.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);
  }

  public boolean startInWriteAction() {
    return true;
  }
}