/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TooBroadCatchInspection extends TooBroadCatchInspectionBase {

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[1];
    final SmartTypePointerManager pointerManager = SmartTypePointerManager.getInstance(context.getProject());
    final List<PsiType> maskedTypes = (List<PsiType>)infos[0];
    final List<InspectionGadgetsFix> fixes = new ArrayList<>();
    for (PsiType thrown : maskedTypes) {
      final String typeText = thrown.getCanonicalText();
      if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(typeText)) {
        fixes.add(new ReplaceWithRuntimeExceptionFix());
      }
      else {
        fixes.add(new AddCatchSectionFix(pointerManager.createSmartTypePointer(thrown), typeText));
      }
    }
    final InspectionGadgetsFix fix = SuppressForTestsScopeFix.build(this, context);
    if (fix != null) {
      fixes.add(fix);
    }
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("too.broad.catch.option"), "onlyWarnOnRootExceptions");
    panel.addCheckbox(InspectionGadgetsBundle.message("overly.broad.throws.clause.ignore.thrown.option"), "ignoreThrown");
    return panel;
  }

  private static class ReplaceWithRuntimeExceptionFix extends InspectionGadgetsFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.catch.clause.for.runtime.exception.quickfix");
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

    private final SmartTypePointer myThrown;
    private final String myText;

    AddCatchSectionFix(SmartTypePointer thrown, String typeText) {
      myThrown = thrown;
      myText = typeText;
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
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiType thrownType = myThrown.getType();
      if (thrownType == null) {
        return;
      }
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
      final PsiCatchSection section = factory.createCatchSection(thrownType, name, myTryStatement);
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
