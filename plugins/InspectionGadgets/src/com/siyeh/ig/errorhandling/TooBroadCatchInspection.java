/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TooBroadCatchInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnRootExceptions = false;

    @NotNull
    public String getID() {
        return "OverlyBroadCatchBlock";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("too.broad.catch.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final List<PsiType> typesMasked = (List<PsiType>)infos[0];
        String typesMaskedString = typesMasked.get(0).getPresentableText();
        if (typesMasked.size() == 1) {
            return InspectionGadgetsBundle.message(
                    "too.broad.catch.problem.descriptor",
                    typesMaskedString);
        } else {
            //Collections.sort(typesMasked);
            final int lastTypeIndex = typesMasked.size() - 1;
            for (int i = 1; i < lastTypeIndex; i++) {
                typesMaskedString += ", ";
                typesMaskedString += typesMasked.get(i).getPresentableText();
            }
            final String lastTypeString =
                    typesMasked.get(lastTypeIndex).getPresentableText();
            return InspectionGadgetsBundle.message(
                    "too.broad.catch.problem.descriptor1",
                    typesMaskedString, lastTypeString);
        }
    }

    @NotNull
    protected InspectionGadgetsFix[] buildFixes(PsiElement location,
                                                Object[] infos) {
        final List<PsiType> maskedTypes = (List<PsiType>)infos[0];
        final PsiTryStatement tryStatement = (PsiTryStatement)infos[1];
        final PsiCatchSection catchSection = (PsiCatchSection)infos[2];
        final List<InspectionGadgetsFix> fixes = new ArrayList();
        for (PsiType thrown : maskedTypes) {
            fixes.add(new AddCatchSectionFix(tryStatement, thrown, catchSection));
        }
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message("too.broad.catch.option"),
                this, "onlyWarnOnRootExceptions");
    }

    private static class AddCatchSectionFix extends InspectionGadgetsFix {

        private final PsiTryStatement myTryStatement;
        private final PsiType myThrown;
        private final PsiCatchSection myBeforeCatchSection;

        AddCatchSectionFix(PsiTryStatement tryStatement, PsiType thrown,
                                  PsiCatchSection catchSection) {
            myTryStatement = tryStatement;
            myThrown = thrown;
            myBeforeCatchSection = catchSection;
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final JavaCodeStyleManager codeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
            final String name = codeStyleManager.suggestUniqueVariableName("e",
                    myTryStatement.getTryBlock(), false);
            final PsiElementFactory factory =
                    JavaPsiFacade.getInstance(project).getElementFactory();
            final PsiCatchSection section =
                    factory.createCatchSection((PsiClassType)myThrown, name,
                            myTryStatement);
            final PsiCatchSection element = (PsiCatchSection)
                    myTryStatement.addBefore(section, myBeforeCatchSection);
            codeStyleManager.shortenClassReferences(element);

            if (isOnTheFly()) {
                final TextRange range = getRangeToSelect(element.getCatchBlock());
                final PsiFile file = element.getContainingFile();
                final Editor editor = FileEditorManager.getInstance(project)
                        .getSelectedTextEditor();
                if (editor == null) {
                    return;
                }
                final Document document = PsiDocumentManager
                        .getInstance(project).getDocument(file);
                if (editor.getDocument() != document) {
                    return;
                }
                editor.getCaretModel().moveToOffset(range.getStartOffset());
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                editor.getSelectionModel().setSelection(range.getStartOffset(),
                        range.getEndOffset());
            }
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("too.broad.catch.quickfix",
                    myThrown.getPresentableText());
        }
    }

    private static TextRange getRangeToSelect (PsiCodeBlock block) {
        PsiElement first = block.getFirstBodyElement();
        if (first instanceof PsiWhiteSpace) {
            first = first.getNextSibling();
        }
        if (first == null) {
            final int offset = block.getTextRange().getStartOffset() + 1;
            return new TextRange(offset, offset);
        }
        PsiElement last = block.getLastBodyElement();
        if (last instanceof PsiWhiteSpace) {
            last = last.getPrevSibling();
        }
        final TextRange textRange;
        if (last == null) {
            textRange = first.getTextRange();
        } else {
            textRange = last.getTextRange();
        }
        return new TextRange(first.getTextRange().getStartOffset(),
                textRange.getEndOffset());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TooBroadCatchVisitor();
    }

    private class TooBroadCatchVisitor
            extends BaseInspectionVisitor {

        @Override public void visitTryStatement(
                @NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock tryBlock = statement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            final Set<PsiType> exceptionsThrown =
                    ExceptionUtils.calculateExceptionsThrown(tryBlock);
            final int numExceptionsThrown = exceptionsThrown.size();
            final Set<PsiType> exceptionsCaught =
                    new HashSet<PsiType>(numExceptionsThrown);
            final PsiCatchSection[] catchSections =
                    statement.getCatchSections();
            for (final PsiCatchSection catchSection : catchSections) {
                final PsiParameter parameter = catchSection.getParameter();
                if (parameter == null) {
                    continue;
                }
                final PsiType typeCaught = parameter.getType();
                if (onlyWarnOnRootExceptions) {
                    final String canonicalText = typeCaught.getCanonicalText();
                    if (!"java.lang.RuntimeException".equals(canonicalText) &&
                        !"java.lang.Exception".equals(canonicalText) &&
                        !"java.lang.Throwable".equals(canonicalText)) {
                        continue;
                    }
                }
                if (exceptionsThrown.contains(typeCaught)) {
                    exceptionsCaught.add(typeCaught);
                }
                final List<PsiType> typesMasked = new ArrayList();
                for (PsiType typeThrown : exceptionsThrown) {
                    if (!exceptionsCaught.contains(typeThrown) &&
                        typeCaught.isAssignableFrom(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                        typesMasked.add(typeThrown);
                    }
                }
                if (!typesMasked.isEmpty()) {
                    final PsiTypeElement typeElement =
                            parameter.getTypeElement();
                    registerError(typeElement, typesMasked, statement,
                            catchSection);
                }
            }
        }
    }
}