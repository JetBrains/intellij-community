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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;

/**
 * @author Maxim.Medvedev
 */
public class GroovyClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  @Nullable
  private static GrNewExpression findNewExpression(@Nullable PsiElement position) {
    if (position == null) return null;
    final PsiElement reference = position.getParent();
    if (!(reference instanceof GrCodeReferenceElement)) return null;

    PsiElement parent = reference.getParent();
    while (parent instanceof GrCodeReferenceElement) parent = parent.getParent();
    if (parent instanceof GrAnonymousClassDefinition) parent = parent.getParent();
    return parent instanceof GrNewExpression ? (GrNewExpression)parent : null;
  }

  @Override
  public void handleInsert(InsertionContext context, JavaPsiClassReferenceElement item) {
    PsiFile file = context.getFile();
    Editor editor = context.getEditor();
    int endOffset = editor.getCaretModel().getOffset();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, endOffset - 1, GrImportStatement.class, false) != null) {
      AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      return;
    }
    PsiElement position = file.findElementAt(endOffset - 1);

    boolean parens = shouldInsertParentheses(position, item.getObject());

    final PsiClass psiClass = item.getObject();
    if (isInVariable(position) || GroovyCompletionContributor.isInPossibleClosureParameter(position)) {
      Project project = context.getProject();
      String qname = psiClass.getQualifiedName();
      String shortName = psiClass.getName();
      if (qname == null) return;

      PsiClass aClass = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedClass(shortName, position);
      if (aClass == null) {
        ((GroovyFileBase)file).addImportForClass(psiClass);
        return;
      }
      else if (aClass == CompletionUtil.getOriginalOrSelf(psiClass)) {
        return;
      }
    }
    AllClassesGetter.TRY_SHORTENING.handleInsert(context, item);

    if (parens && context.getCompletionChar() != '[') {
      int identifierEnd = context.getTailOffset();

      JavaCompletionUtil.insertParentheses(context, item, false, GroovyCompletionUtil.hasConstructorParameters(psiClass));

      if (context.getCompletionChar() == '<') {
        context.getDocument().insertString(identifierEnd, "<>");
        context.setAddCompletionChar(false);
        context.getEditor().getCaretModel().moveToOffset(identifierEnd + 1);
      }
    }

  }

  private static boolean shouldInsertParentheses(PsiElement position, PsiClass psiClass) {
    final GrNewExpression newExpression = findNewExpression(position);
    return newExpression != null && JavaCompletionUtil
      .isDefinitelyExpected(psiClass, GroovyExpectedTypesProvider.getDefaultExpectedTypes(newExpression), position);
  }

  private static boolean isInVariable(PsiElement position) {
    GrVariable variable = PsiTreeUtil.getParentOfType(position, GrVariable.class);
    return variable != null && variable.getTypeElementGroovy() == null && position == variable.getNameIdentifierGroovy();
  }
}
