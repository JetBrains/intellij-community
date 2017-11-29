/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageHelper;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.daemon.impl.quickfix.GuessTypeParameters;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

/**
 * @author Max Medvedev
 */
public class GroovyCreateFieldFromUsageHelper extends CreateFieldFromUsageHelper {
  @Override
  public Template setupTemplateImpl(PsiField f,
                                    Object expectedTypes,
                                    PsiClass targetClass,
                                    Editor editor,
                                    PsiElement context,
                                    boolean createConstantField,
                                    @NotNull PsiSubstitutor substitutor) {
    GrVariableDeclaration fieldDecl = (GrVariableDeclaration)f.getParent();
    GrField field = (GrField)fieldDecl.getVariables()[0];

    TemplateBuilderImpl builder = new TemplateBuilderImpl(fieldDecl);

    Project project = context.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    if (expectedTypes instanceof TypeConstraint[]) {
      GrTypeElement typeElement = fieldDecl.getTypeElementGroovy();
      assert typeElement != null;
      ChooseTypeExpression expr = new ChooseTypeExpression((TypeConstraint[])expectedTypes, PsiManager.getInstance(project),
                                                           typeElement.getResolveScope());
      builder.replaceElement(typeElement, expr);
    }
    else if (expectedTypes instanceof ExpectedTypeInfo[]) {
      new GuessTypeParameters(project, factory, builder, substitutor).setupTypeElement(field.getTypeElement(), (ExpectedTypeInfo[])expectedTypes,
                                                                                       context, targetClass);
    }
    if (createConstantField) {
      field.setInitializerGroovy(factory.createExpressionFromText("0", null));
      builder.replaceElement(field.getInitializerGroovy(), new EmptyExpression());
    }

    fieldDecl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(fieldDecl);
    Template template = builder.buildTemplate();

    TextRange range = fieldDecl.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    if (expectedTypes instanceof ExpectedTypeInfo[]) {
      if (!Registry.is("ide.create.field.enable.shortening") && ((ExpectedTypeInfo[])expectedTypes).length > 1) {
        template.setToShortenLongNames(false);
      }
    }
    return template;
  }

  @Override
  public PsiField insertFieldImpl(@NotNull PsiClass targetClass, @NotNull PsiField field, @NotNull PsiElement place) {
    if (targetClass instanceof GroovyScriptClass) {
      PsiElement added = targetClass.getContainingFile().add(field.getParent());
      return (PsiField)((GrVariableDeclaration)added).getVariables()[0];
    }
    else {
      return (PsiField)targetClass.add(field);
    }
  }
}
