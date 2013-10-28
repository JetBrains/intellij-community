/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TooBroadCatchInspection extends TooBroadCatchInspectionBase {

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<PsiClass> maskedTypes = (List<PsiClass>)infos[0];
    final List<InspectionGadgetsFix> fixes = new ArrayList();
    for (PsiClass thrown : maskedTypes) {
      if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(maskedTypes.get(0).getQualifiedName())) {
        fixes.add(new ReplaceWithRuntimeExceptionFix());
      }
      else {
        fixes.add(new AddCatchSectionFix(thrown));
      }
    }
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("too.broad.catch.option"), "onlyWarnOnRootExceptions");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.in.test.code"), "ignoreInTestCode");
    panel.addCheckbox(InspectionGadgetsBundle.message("overly.broad.throws.clause.ignore.thrown.option"), "ignoreThrown");
    return panel;
  }

  private static class ReplaceWithRuntimeExceptionFix extends InspectionGadgetsFix {
    @NotNull
    @Override
    public String getName() {
      return "Replace with 'catch' clause for 'RuntimeException'";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement)) {
        return;
      }
      final PsiTypeElement typeElement = (PsiTypeElement)element;
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiClassType type = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION);
      final PsiTypeElement newTypeElement = factory.createTypeElement(type);
      typeElement.replace(newTypeElement);
    }
  }

  private static class AddCatchSectionFix extends InspectionGadgetsFix {

    private final SmartPsiElementPointer<PsiClass> myThrown;
    private final String myText;

    AddCatchSectionFix(PsiClass thrown) {
      myThrown = SmartPointerManager.getInstance(thrown.getProject()).createSmartPsiElementPointer(thrown);
      myText = thrown.getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("too.broad.catch.quickfix", myText);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Add 'catch' clause";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement typeElement = descriptor.getPsiElement();
      if (typeElement == null) {
        return;
      }
      final PsiElement catchParameter = typeElement.getParent();
      if (!(catchParameter instanceof PsiParameter)) {
        return;
      }
      final PsiElement catchBlock = ((PsiParameter)catchParameter).getDeclarationScope();
      if (!(catchBlock instanceof PsiCatchSection)) {
        return;
      }
      final PsiCatchSection myBeforeCatchSection = (PsiCatchSection)catchBlock;
      final PsiTryStatement myTryStatement = myBeforeCatchSection.getTryStatement();
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final String name = codeStyleManager.suggestUniqueVariableName("e", myTryStatement.getTryBlock(), false);
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiClass aClass = myThrown.getElement();
      if (aClass == null) {
        return;
      }
      final PsiCatchSection section = factory.createCatchSection(factory.createType(aClass), name, myTryStatement);
      final PsiCatchSection element = (PsiCatchSection)myTryStatement.addBefore(section, myBeforeCatchSection);
      codeStyleManager.shortenClassReferences(element);

      if (isOnTheFly()) {
        final PsiCodeBlock newBlock = element.getCatchBlock();
        assert newBlock != null;
        final TextRange range = SurroundWithUtil.getRangeToSelect(newBlock);
        final PsiFile file = element.getContainingFile();
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
          return;
        }
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (editor.getDocument() != document) {
          return;
        }
        editor.getCaretModel().moveToOffset(range.getStartOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
  }
}
