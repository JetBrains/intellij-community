/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix;
import com.intellij.codeInsight.template.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.template.expressions.ParameterNameExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class IntentionUtils {

  private static final Logger LOG = Logger.getInstance(IntentionUtils.class);

  public static void replaceExpression(@NotNull String newExpression, @NotNull GrExpression expression) throws IncorrectOperationException {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
    final GrExpression newCall = factory.createExpressionFromText(newExpression);
    expression.replaceWithExpression(newCall, true);
  }

  public static GrStatement replaceStatement(@NonNls @NotNull String newStatement, @NonNls @NotNull GrStatement statement)
    throws IncorrectOperationException {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(statement.getProject());
    final GrStatement newCall = (GrStatement)factory.createTopElementFromText(newStatement);
    return statement.replaceWithStatement(newCall);
  }

  public static void createTemplateForMethod(PsiType[] argTypes,
                                             ChooseTypeExpression[] paramTypesExpressions,
                                             PsiMethod method,
                                             PsiClass owner,
                                             TypeConstraint[] constraints,
                                             boolean isConstructor,
                                             @NotNull final PsiElement context) {

    final Project project = owner.getProject();
    PsiTypeElement typeElement = method.getReturnTypeElement();
    ChooseTypeExpression expr =
      new ChooseTypeExpression(constraints, PsiManager.getInstance(project), context.getResolveScope(),
                               method.getLanguage() == GroovyFileType.GROOVY_LANGUAGE
      );
    TemplateBuilderImpl builder = new TemplateBuilderImpl(method);
    if (!isConstructor) {
      assert typeElement != null;
      builder.replaceElement(typeElement, expr);
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    assert parameters.length == argTypes.length;
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiTypeElement parameterTypeElement = parameter.getTypeElement();
      builder.replaceElement(parameterTypeElement, paramTypesExpressions[i]);
      builder.replaceElement(parameter.getNameIdentifier(), new ParameterNameExpression(null));
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiElement lbrace = body.getLBrace();
      assert lbrace != null;
      builder.setEndVariableAfter(lbrace);
    }
    else {
      builder.setEndVariableAfter(method.getParameterList());
    }

    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    Template template = builder.buildTemplate();

    final PsiFile targetFile = owner.getContainingFile();
    final Editor newEditor = QuickfixUtil.positionCursor(project, targetFile, method);
    TextRange range = method.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);


    TemplateEditingListener templateListener = new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
            final int offset = newEditor.getCaretModel().getOffset();
            PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset - 1, PsiMethod.class, false);
            if (context instanceof PsiMethod) {
              final PsiTypeParameter[] typeParameters = ((PsiMethod)context).getTypeParameters();
              if (typeParameters.length > 0) {
                for (PsiTypeParameter typeParameter : typeParameters) {
                  if (CreateMethodFromUsageFix.checkTypeParam(method, typeParameter)) {
                    final JVMElementFactory factory = JVMElementFactories.getFactory(method.getLanguage(), method.getProject());
                    PsiTypeParameterList list = method.getTypeParameterList();
                    if (list == null) {
                      PsiTypeParameterList newList = factory.createTypeParameterList();
                      list = (PsiTypeParameterList)method.addAfter(newList, method.getModifierList());
                    }
                    list.add(factory.createTypeParameter(typeParameter.getName(), typeParameter.getExtendsList().getReferencedTypes()));
                  }
                }
              }
            }
            if (method != null) {
              try {
                final boolean hasNoReturnType = method.getReturnTypeElement() == null && method instanceof GrMethod;
                if (hasNoReturnType) {
                  ((GrMethod)method).setReturnType(PsiType.VOID);
                }
                if (method.getBody() != null) {
                  FileTemplateManager templateManager = FileTemplateManager.getInstance();
                  FileTemplate fileTemplate = templateManager.getCodeTemplate(GroovyTemplates.GROOVY_FROM_USAGE_METHOD_BODY);
                  CreateFromUsageUtils.setupMethodBody(method, method.getContainingClass(), fileTemplate);
                }
                if (hasNoReturnType) {
                  ((GrMethod)method).setReturnType(null);
                }
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }

              CreateFromUsageUtils.setupEditor(method, newEditor);
            }
          }
        });
      }
    };
    manager.startTemplate(newEditor, template, templateListener);
  }
}
