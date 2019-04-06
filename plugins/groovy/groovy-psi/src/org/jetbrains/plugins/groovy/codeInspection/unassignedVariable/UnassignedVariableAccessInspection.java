// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.unassignedVariable;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;

/**
 * @author ven
 */
public class UnassignedVariableAccessInspection extends GroovyLocalInspectionBase {

  @NotNull
  @Override
  public String getShortName() {
    // used to enable inspection in tests
    // remove when inspection class will match its short name
    return "GroovyVariableNotAssigned";
  }

  public boolean myIgnoreBooleanExpressions = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(GroovyInspectionBundle.message("ignore.boolean.expressions"), "myIgnoreBooleanExpressions");
    return optionsPanel;
  }

  @Override
  protected void check(@NotNull GrControlFlowOwner owner, @NotNull ProblemsHolder problemsHolder) {
    Instruction[] flow = owner.getControlFlow();
    ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(flow, true);
    for (ReadWriteVariableInstruction read : reads) {
      PsiElement element = read.getElement();
      if (element instanceof GroovyPsiElement && !(element instanceof GrClosableBlock)) {
        String name = read.getDescriptor().getName();
        GroovyPsiElement property = ResolveUtil.resolveProperty((GroovyPsiElement)element, name);
        if (property != null &&
            !(property instanceof PsiParameter) &&
            !(property instanceof PsiField) &&
            PsiTreeUtil.isAncestor(owner, property, false) &&
            !(myIgnoreBooleanExpressions && isBooleanCheck(element))
          ) {
          problemsHolder.registerProblem(element, GroovyInspectionBundle.message("unassigned.access.tooltip", name));
        }
      }
    }
  }

  private static boolean isBooleanCheck(PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof GrIfStatement && ((GrIfStatement)parent).getCondition() == element ||
           parent instanceof GrWhileStatement && ((GrWhileStatement)parent).getCondition() == element ||
           parent instanceof GrTraditionalForClause && ((GrTraditionalForClause)parent).getCondition() == element ||
           isLogicalExpression(parent) ||
           parent instanceof GrUnaryExpression && ((GrUnaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mBNOT ||
           isCheckForNull(parent, element);
  }

  private static boolean isLogicalExpression(PsiElement parent) {
    return parent instanceof GrBinaryExpression &&
           (((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mLAND ||
            ((GrBinaryExpression)parent).getOperationTokenType() == GroovyTokenTypes.mLOR);
  }

  private static boolean isCheckForNull(PsiElement parent, PsiElement element) {
    if (!(parent instanceof GrBinaryExpression)) return false;

    final IElementType tokenType = ((GrBinaryExpression)parent).getOperationTokenType();
    if (!(tokenType == GroovyTokenTypes.mEQUAL || tokenType == GroovyTokenTypes.mNOT_EQUAL)) return false;
    if (element == ((GrBinaryExpression)parent).getLeftOperand()) {
      final GrExpression rightOperand = ((GrBinaryExpression)parent).getRightOperand();
      return rightOperand != null && GrInspectionUtil.isNull(rightOperand);
    }
    else {
      return GrInspectionUtil.isNull(((GrBinaryExpression)parent).getLeftOperand());
    }
  }
}
