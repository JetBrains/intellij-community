/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BlockUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DeleteCatchSectionFix extends InspectionGadgetsFix {

  private final boolean removeTryCatch;

  public DeleteCatchSectionFix(boolean removeTryCatch) {
    this.removeTryCatch = removeTryCatch;
  }

  @Override
  @NotNull
  public String getName() {
    if (removeTryCatch) {
      return InspectionGadgetsBundle.message("remove.try.catch.quickfix");
    }
    else {
      return InspectionGadgetsBundle.message("delete.catch.section.quickfix");
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Delete catch statement";
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiParameter)) {
      return;
    }
    final PsiParameter parameter = (PsiParameter)parent;
    final PsiElement grandParent = parameter.getParent();
    if (!(grandParent instanceof PsiCatchSection)) {
      return;
    }
    final PsiCatchSection catchSection = (PsiCatchSection)grandParent;
    if (removeTryCatch) {
      unwrapTryBlock(catchSection.getTryStatement());
    }
    else {
      catchSection.delete();
    }
  }

  public static void unwrapTryBlock(PsiTryStatement tryStatement) {
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return;
    }
    final PsiElement parent = tryStatement.getParent();
    boolean singleStatement = false;
    if (parent instanceof PsiStatement) {
      final PsiStatement[] statements = tryBlock.getStatements();
      if (statements.length == 1 && !(statements[0] instanceof PsiDeclarationStatement)) {
        singleStatement = true;
      }
      else {
        tryStatement = BlockUtils.expandSingleStatementToBlockStatement(tryStatement);
      }
    }
    else if (parent instanceof PsiCodeBlock) {
      if (DeclarationSearchUtils.containsConflictingDeclarations(tryBlock, (PsiCodeBlock)parent)) {
        tryStatement = BlockUtils.expandSingleStatementToBlockStatement(tryStatement);
      }
    }
    else {
      return;
    }

    tryBlock = tryStatement.getTryBlock();
    assert tryBlock != null;
    final PsiElement first = singleStatement ? skip(tryBlock.getFirstBodyElement(), true) : tryBlock.getFirstBodyElement();
    final PsiElement last = singleStatement? skip(tryBlock.getLastBodyElement(), false) : tryBlock.getLastBodyElement();
    assert first != null && last != null;
    tryStatement.getParent().addRangeBefore(first, last, tryStatement);
    tryStatement.delete();
  }

  private static PsiElement skip(PsiElement element, boolean forward) {
    if (!(element instanceof PsiWhiteSpace)) {
      return element;
    }
    return forward ? element.getNextSibling() : element.getPrevSibling();
  }
}
