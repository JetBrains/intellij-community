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
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.utils.BoolUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;


public abstract class Intention implements IntentionAction {
  private final PsiElementPredicate predicate;

  /**
   * @noinspection AbstractMethodCallInConstructor, OverridableMethodCallInConstructor
   */
  protected Intention() {
    super();
    predicate = getElementPredicate();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!QuickfixUtil.ensureFileWritable(project, file)) {
      return;
    }
    final PsiElement element = findMatchingElement(file, editor);
    if (element == null) {
      return;
    }
    assert element.isValid() : element;
    processIntention(element, project, editor);
  }

  protected abstract void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException;

  @NotNull
  protected abstract PsiElementPredicate getElementPredicate();


  protected static void replaceExpressionWithNegatedExpressionString(@NotNull String newExpression, @NotNull GrExpression expression) throws IncorrectOperationException {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());

    GrExpression expressionToReplace = expression;
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expression);
      expString = newExpression;
    }
    else {
      expString = "!(" + newExpression + ')';
    }
    final GrExpression newCall =
      factory.createExpressionFromText(expString);
    assert expressionToReplace != null;
    expressionToReplace.replaceWithExpression(newCall, true);
  }


  @Nullable
  PsiElement findMatchingElement(PsiFile file, Editor editor) {
    if (!file.getViewProvider().getLanguages().contains(GroovyLanguage.INSTANCE)) {
      return null;
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();

      if (0 <= start && start <= end) {
        TextRange selectionRange = new TextRange(start, end);
        PsiElement element = PsiImplUtil.findElementInRange(file, start, end, PsiElement.class);
        while (element != null && element.getTextRange() != null && selectionRange.contains(element.getTextRange())) {
          if (predicate.satisfiedBy(element)) return element;
          element = element.getParent();
        }
      }
    }

    final int position = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(position);
    while (element != null) {
      if (predicate.satisfiedBy(element)) return element;
      if (isStopElement(element)) break;
      element = element.getParent();
    }

    element = file.findElementAt(position - 1);
    while (element != null) {
      if (predicate.satisfiedBy(element)) return element;
      if (isStopElement(element)) return null;
      element = element.getParent();
    }

    return null;
  }

  protected boolean isStopElement(PsiElement element) {
    return element instanceof PsiFile;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return findMatchingElement(file, editor) != null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private String getPrefix() {
    final Class<? extends Intention> aClass = getClass();
    final String name = aClass.getSimpleName();
    final StringBuilder buffer = new StringBuilder(name.length() + 10);
    buffer.append(Character.toLowerCase(name.charAt(0)));
    for (int i = 1; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        buffer.append('.');
        buffer.append(Character.toLowerCase(c));
      }
      else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  @Override
  @NotNull
  public String getText() {
    return GroovyIntentionsBundle.message(getPrefix() + ".name");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyIntentionsBundle.message(getPrefix() + ".family.name");
  }
}
