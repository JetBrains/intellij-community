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
package com.intellij.refactoring.inline;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.containers.FilteringIterator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * @author gregsh
 */
public class InlinePropertyHandler extends JavaInlineActionHandler {
  public static final String REFACTORING_NAME = PropertiesBundle.message("inline.property.refactoring");
  public static final String REFACTORING_ID = "refactoring.inline.property";

  public boolean canInlineElement(PsiElement element) {
    if (PsiUtil.isJavaToken(element, JavaTokenType.STRING_LITERAL)) {
      PsiReference[] references = element.getParent().getReferences();
      return ContainerUtil.find(references, FilteringIterator.instanceOf(PropertyReference.class)) != null;
    }
    return element instanceof IProperty;
  }

  public void inlineElement(final Project project, Editor editor, PsiElement psiElement) {
    if (!(psiElement instanceof IProperty)) return;

    IProperty property = (IProperty)psiElement;
    final String propertyValue = property.getValue();
    if (propertyValue == null) return;

    final List<PsiElement> occurrences = Collections.synchronizedList(ContainerUtil.<PsiElement>newArrayList());
    final Collection<PsiFile> containingFiles = Collections.synchronizedSet(new HashSet<PsiFile>());
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
      CommonRefactoringUtil.showErrorHint(project, editor, "Property has non-method usages", REFACTORING_NAME, null);
    }
    if (occurrences.isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Property has no usages", REFACTORING_NAME, null);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String occurrencesString = RefactoringBundle.message("occurrences.string", occurrences.size());
      String question =
        PropertiesBundle.message("inline.property.confirmation", property.getName(), propertyValue) + " " + occurrencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(REFACTORING_NAME, question, HelpID.INLINE_VARIABLE,
                                                                     "OptionPane.questionIcon", true, project);
      if (!dialog.showAndGet()) {
        return;
      }
    }

    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(psiElement.copy());

    WriteCommandAction.writeCommandAction(project, containingFiles.toArray(PsiFile.EMPTY_ARRAY)).withName(REFACTORING_NAME).run(() -> {
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, data);
      PsiLiteral stringLiteral = (PsiLiteral)JavaPsiFacade.getInstance(project).getElementFactory().
        createExpressionFromText("\"" + StringUtil.escapeStringCharacters(propertyValue) + "\"", null);
      for (PsiElement occurrence : occurrences) {
        occurrence.replace(stringLiteral.copy());
      }
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, null);
    });
  }
}
