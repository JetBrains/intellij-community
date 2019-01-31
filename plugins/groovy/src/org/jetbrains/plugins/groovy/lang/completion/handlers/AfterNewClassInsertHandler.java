// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.completion.handlers;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.ConstructorInsertHandler;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaCompletionFeatures;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author Maxim.Medvedev
 */
public class AfterNewClassInsertHandler implements InsertHandler<LookupElement> {
  private final PsiClassType myClassType;
  private final boolean myTriggerFeature;

  public AfterNewClassInsertHandler(PsiClassType classType, boolean triggerFeature) {
    myClassType = classType;
    myTriggerFeature = triggerFeature;
  }

  @Override
  public void handleInsert(@NotNull final InsertionContext context, @NotNull LookupElement item) {
    final PsiClassType.ClassResolveResult resolveResult = myClassType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null || !psiClass.isValid()) {
      return;
    }

    GroovyPsiElement place =
      PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GroovyPsiElement.class, false);
    boolean hasParams = place != null && GroovyCompletionUtil.hasConstructorParameters(psiClass, place);
    if (myTriggerFeature) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
    }

    if (hasParams) {
      ParenthesesInsertHandler.WITH_PARAMETERS.handleInsert(context, item);
    }
    else {
      ParenthesesInsertHandler.NO_PARAMETERS.handleInsert(context, item);
    }

    shortenRefsInGenerics(context);
    if (hasParams) {
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final Editor editor = context.getEditor();
      final int offset = context.getTailOffset();
      editor.getDocument().insertString(offset, " {}");
      editor.getCaretModel().moveToOffset(offset + 2);

      context.setLaterRunnable(generateAnonymousBody(editor, context.getFile()));

    }
  }

  private static void shortenRefsInGenerics(InsertionContext context) {
    int offset = context.getStartOffset();

    final String text = context.getDocument().getText();
    while (text.charAt(offset) != '<' && text.charAt(offset) != '(') {
      offset++;
    }
    if (text.charAt(offset) == '<') {
      GroovyCompletionUtil.shortenReference(context.getFile(), offset);
    }
  }

  @Nullable
  private static Runnable generateAnonymousBody(final Editor editor, final PsiFile file) {
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    PsiElement parent = element.getParent().getParent();
    if (!(parent instanceof PsiAnonymousClass)) return null;

    return ConstructorInsertHandler.genAnonymousBodyFor((PsiAnonymousClass)parent, editor, file, project);
  }

}
