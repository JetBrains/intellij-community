/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TooBroadCatchInspection extends BaseInspection {

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
        final PsiTryStatement tryStatement =
                PsiTreeUtil.getParentOfType((PsiElement)infos[0],
                        PsiTryStatement.class);
        assert tryStatement != null;
        final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
        assert tryBlock != null;
        final Set<PsiType> exceptionsThrown =
                ExceptionUtils.calculateExceptionsThrown(tryBlock);
        final int numExceptionsThrown = exceptionsThrown.size();
        final Set<PsiType> exceptionsCaught =
                new HashSet<PsiType>(numExceptionsThrown);
        final PsiParameter[] parameters =
                tryStatement.getCatchBlockParameters();
        final List<String> typesMasked = new ArrayList<String>();
        for (final PsiParameter parameter : parameters) {
            final PsiType typeCaught = parameter.getType();
            if (exceptionsThrown.contains(typeCaught)) {
                exceptionsCaught.add(typeCaught);
            }
            if (parameter.equals(infos[0])) {
                for (PsiType typeThrown : exceptionsThrown) {
                    if (exceptionsCaught.contains(typeThrown)) {
                        //don't do anything
                    } else if (typeCaught.equals(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                    } else if (typeCaught.isAssignableFrom(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                        typesMasked.add(typeThrown.getPresentableText());
                    }
                }
            }
        }
        if (typesMasked.size() == 1) {
            return InspectionGadgetsBundle.message(
                    "too.broad.catch.problem.descriptor", typesMasked.get(0));
        } else {
            Collections.sort(typesMasked);
            String typesMaskedString = "";
            for (int i = 0; i < typesMasked.size() - 1; i++) {
                if (i != 0) {
                    typesMaskedString += ", ";
                }
                typesMaskedString += typesMasked.get(i);
            }
            return InspectionGadgetsBundle.message(
                    "too.broad.catch.problem.descriptor1",
                    typesMaskedString, typesMasked.get(typesMasked.size() - 1));
        }
    }

  @NotNull
  protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
    PsiTypeElement typeElement = (PsiTypeElement)location;
    PsiParameter catchParameter = (PsiParameter)typeElement.getParent();

    PsiCatchSection catchSection = (PsiCatchSection)catchParameter.getParent();
    PsiTryStatement tryStatement = catchSection.getTryStatement();
    PsiCodeBlock block = tryStatement.getTryBlock();
    assert block != null;
    Set<PsiType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(block);
    List<InspectionGadgetsFix> fixes = new ArrayList<InspectionGadgetsFix>();
    for (PsiType thrown : exceptionsThrown) {
      if (!isCaughtBefore(thrown, tryStatement, catchSection)) {
        fixes.add(new AddCatchSectionFix(tryStatement, thrown, catchSection));
      }
    }
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  private static boolean isCaughtBefore(PsiType thrown, PsiTryStatement tryStatement, PsiCatchSection catchSection) {
    PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    for (PsiCatchSection section : catchSections) {
      if (catchSection == section) return false;
      PsiType type = section.getCatchType();
      if (type == null) continue;
      if (type.isAssignableFrom(thrown)) return true;
    }
    return false;
  }

  private static class AddCatchSectionFix extends InspectionGadgetsFix {
    private final PsiTryStatement myTryStatement;
    private final PsiType myThrown;
    private final PsiCatchSection myBeforeCatchSection;

    public AddCatchSectionFix(PsiTryStatement tryStatement, PsiType thrown, PsiCatchSection catchSection) {
      myTryStatement = tryStatement;
      myThrown = thrown;
      myBeforeCatchSection = catchSection;
    }

    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      String name = codeStyleManager.suggestUniqueVariableName("e", myTryStatement.getTryBlock(), false);
      PsiCatchSection section =
        myTryStatement.getManager().getElementFactory().createCatchSection((PsiClassType)myThrown, name, myTryStatement);
      PsiCatchSection element = (PsiCatchSection)myTryStatement.addBefore(section, myBeforeCatchSection);
      codeStyleManager.shortenClassReferences(element);

      if (isOnTheFly()) {
        TextRange range = getRangeToSelect(element.getCatchBlock());
        PsiFile file = element.getContainingFile();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) return;
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (editor.getDocument() != document) return;
        editor.getCaretModel().moveToOffset(range.getStartOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("add.catch.clause.for.0", myThrown.getPresentableText());
    }
  }

  private static TextRange getRangeToSelect (PsiCodeBlock block) {
    PsiElement first = block.getFirstBodyElement();
    if (first instanceof PsiWhiteSpace) {
      first = first.getNextSibling();
    }
    if (first == null) {
      int offset = block.getTextRange().getStartOffset() + 1;
      return new TextRange(offset, offset);
    }
    PsiElement last = block.getRBrace().getPrevSibling();
    if (last instanceof PsiWhiteSpace) {
      last = last.getPrevSibling();
    }
    return new TextRange(first.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
  }

  public BaseInspectionVisitor buildVisitor() {
        return new TooBroadCatchVisitor();
    }

    private static class TooBroadCatchVisitor
            extends BaseInspectionVisitor {

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
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
            final PsiParameter[] parameters =
                    statement.getCatchBlockParameters();
            for (final PsiParameter parameter : parameters) {
                final PsiType typeCaught = parameter.getType();
                if (exceptionsThrown.contains(typeCaught)) {
                    exceptionsCaught.add(typeCaught);
                }
                for (PsiType typeThrown : exceptionsThrown) {
                    if (exceptionsCaught.contains(typeThrown)) {
                        //don't do anything
                    } else if (typeCaught.equals(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                    } else if (typeCaught.isAssignableFrom(typeThrown)) {
                        exceptionsCaught.add(typeCaught);
                        final PsiTypeElement typeElement =
                                parameter.getTypeElement();
                        registerError(typeElement, parameter);
                        return;
                    }
                }
            }
        }
    }
}