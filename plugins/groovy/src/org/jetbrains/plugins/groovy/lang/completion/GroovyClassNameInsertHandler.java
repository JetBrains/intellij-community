// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
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
  private static @Nullable GrNewExpression findNewExpression(@Nullable PsiElement position) {
    if (position == null) return null;
    final PsiElement reference = position.getParent();
    if (!(reference instanceof GrCodeReferenceElement)) return null;

    PsiElement parent = reference.getParent();
    while (parent instanceof GrCodeReferenceElement) parent = parent.getParent();
    if (parent instanceof GrAnonymousClassDefinition) parent = parent.getParent();
    return parent instanceof GrNewExpression ? (GrNewExpression)parent : null;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull JavaPsiClassReferenceElement item) {
    PsiFile file = context.getFile();
    Editor editor = context.getEditor();
    int endOffset = editor.getCaretModel().getOffset();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, endOffset - 1, GrImportStatement.class, false) != null ||
        !(file instanceof GroovyFileBase)) {
      AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      return;
    }

    PsiDocumentManager.getInstance(context.getProject()).commitDocument(editor.getDocument());

    PsiElement position = file.findElementAt(endOffset - 1);

    boolean parens = shouldInsertParentheses(position);

    final PsiClass psiClass = item.getObject();
    if (isInVariable(position) || GroovyCompletionUtil.isInPossibleClosureParameter(position)) {
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

      GroovyPsiElement place = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GroovyPsiElement.class, false);
      JavaCompletionUtil.insertParentheses(context, item, false, place != null && GroovyCompletionUtil.hasConstructorParameters(psiClass, place));

      if (context.getCompletionChar() == '<' || psiClass.hasTypeParameters() && context.getCompletionChar() != '(') {
        context.setAddCompletionChar(false);
        JavaCompletionUtil.promptTypeArgs(context, identifierEnd);
      }
    }

  }

  private static boolean shouldInsertParentheses(PsiElement position) {
    final GrNewExpression newExpression = findNewExpression(position);
    return newExpression != null && ContainerUtil.findInstance(GroovyExpectedTypesProvider.getDefaultExpectedTypes(newExpression),
                                                               PsiArrayType.class) == null;
  }

  private static boolean isInVariable(@Nullable PsiElement position) {
    if (position == null) {
      return false;
    }

    final PsiElement parent = position.getParent();
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getTypeElementGroovy() == null && position == ((GrVariable)parent).getNameIdentifierGroovy();
    }
    if (parent instanceof GrCatchClause) {
      return ((GrCatchClause)parent).getParameter() == null;
    }

    return false;
  }
}
