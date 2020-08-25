// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author gregsh
 */
public class InlinePropertyHandler extends JavaInlineActionHandler {
  public static final String REFACTORING_ID = "refactoring.inline.property";

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (PsiUtil.isJavaToken(element, JavaTokenType.STRING_LITERAL)) {
      PsiReference[] references = element.getParent().getReferences();
      return ContainerUtil.findInstance(references, PropertyReference.class) != null;
    }
    return element instanceof IProperty;
  }

  @Override
  public void inlineElement(final Project project, Editor editor, PsiElement psiElement) {
    if (!(psiElement instanceof IProperty)) return;

    IProperty property = (IProperty)psiElement;
    final String propertyValue = property.getValue();
    if (propertyValue == null) return;

    final List<PsiElement> occurrences = Collections.synchronizedList(new ArrayList<>());
    final Collection<PsiFile> containingFiles = Collections.synchronizedSet(new HashSet<>());
    containingFiles.add(psiElement.getContainingFile());
    boolean result = ReferencesSearch.search(psiElement).forEach(
      psiReference -> {
        PsiElement element = psiReference.getElement();
        PsiElement parent = element.getParent();
        if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiMethodCallExpression) {
          if (((PsiExpressionList)parent).getExpressionCount() == 1) {
            occurrences.add(parent.getParent());
            containingFiles.add(element.getContainingFile());
            return true;
          }
        }
        return false;
      }
    );

    if (!result) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaI18nBundle.message("error.hint.property.has.non.method.usages"), getRefactoringName(), null);
    }
    if (occurrences.isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaI18nBundle.message("error.hint.property.has.no.usages"), getRefactoringName(), null);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String occurrencesString = RefactoringBundle.message("occurrences.string", occurrences.size());
      String question =
        JavaI18nBundle.message("inline.property.confirmation", property.getName(), propertyValue) + " " + occurrencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(getRefactoringName(), question, HelpID.INLINE_VARIABLE,
                                                                     "OptionPane.questionIcon", true, project);
      if (!dialog.showAndGet()) {
        return;
      }
    }

    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(psiElement.copy());

    WriteCommandAction.writeCommandAction(project, containingFiles.toArray(PsiFile.EMPTY_ARRAY)).withName(getRefactoringName()).run(() -> {
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, data);
      PsiLiteral stringLiteral = (PsiLiteral)JavaPsiFacade.getInstance(project).getElementFactory().
        createExpressionFromText("\"" + StringUtil.escapeStringCharacters(propertyValue) + "\"", null);
      for (PsiElement occurrence : occurrences) {
        occurrence.replace(stringLiteral.copy());
      }
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, null);
    });
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return getRefactoringName();
  }

  public static @NlsActions.ActionText String getRefactoringName() {
    return JavaI18nBundle.message("inline.property.refactoring");
  }
}
