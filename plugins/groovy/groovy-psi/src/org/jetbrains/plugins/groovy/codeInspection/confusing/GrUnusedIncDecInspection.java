// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Iterator;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.VariableDescriptorFactory.createDescriptor;

/**
 * @author Max Medvedev
 */
public class GrUnusedIncDecInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(GrUnusedIncDecInspection.class);

  @NotNull
  @Override
  public String getShortName() {
    // used to enable inspection in tests
    // remove when inspection class will match its short name
    return "GroovyUnusedIncOrDec";
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new GrUnusedIncDecInspectionVisitor();
  }

  private static class GrUnusedIncDecInspectionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
      super.visitUnaryExpression(expression);

      IElementType opType = expression.getOperationTokenType();
      if (opType != GroovyTokenTypes.mINC && opType != GroovyTokenTypes.mDEC) return;

      GrExpression operand = expression.getOperand();
      if (!(operand instanceof GrReferenceExpression)) return;

      PsiElement resolved = ((GrReferenceExpression)operand).resolve();
      if (!(resolved instanceof GrVariable) || resolved instanceof GrField) return;

      final GrControlFlowOwner owner = ControlFlowUtils.findControlFlowOwner(expression);
      assert owner != null;
      GrControlFlowOwner ownerOfDeclaration = ControlFlowUtils.findControlFlowOwner(resolved);
      if (ownerOfDeclaration != owner) return;

      GroovyControlFlow groovyFlow = ControlFlowUtils.getGroovyControlFlow(owner);

      final Instruction cur = ControlFlowUtils.findInstruction(operand, groovyFlow.getFlow());

      if (cur == null) {
        LOG.error("no instruction found in flow." + "operand: " + operand.getText(), new Attachment("", owner.getText()));
      }

      //get write access for inc or dec
      Iterable<? extends Instruction> successors = cur.allSuccessors();
      Iterator<? extends Instruction> iterator = successors.iterator();
      LOG.assertTrue(iterator.hasNext());
      Instruction writeAccess = iterator.next();
      LOG.assertTrue(!iterator.hasNext());

      int variableIndex = groovyFlow.getIndex(createDescriptor((GrVariable)resolved));
      List<ReadWriteVariableInstruction> accesses = ControlFlowUtils.findAccess(variableIndex, true, false, writeAccess);

      boolean allAreWrite = true;
      for (ReadWriteVariableInstruction access : accesses) {
        if (!access.isWrite()) {
          allAreWrite = false;
          break;
        }
      }


      if (allAreWrite) {
        if (expression.isPostfix() && PsiUtil.isExpressionUsed(expression)) {
          registerError(expression.getOperationToken(),
                        GroovyBundle.message("unused.0", expression.getOperationToken().getText()),
                        new LocalQuickFix[]{new ReplacePostfixIncWithPrefixFix(expression), new RemoveIncOrDecFix(expression)}, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
        else if (!PsiUtil.isExpressionUsed(expression)) {
          registerError(expression.getOperationToken(),
                        GroovyBundle.message("unused.0", expression.getOperationToken().getText()), LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    }

    private static class RemoveIncOrDecFix implements LocalQuickFix {
      private final @IntentionFamilyName String myMessage;

      RemoveIncOrDecFix(GrUnaryExpression expression) {
        myMessage = GroovyBundle.message("remove.0", expression.getOperationToken().getText());
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return myMessage;
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        GrUnaryExpression expr = findUnaryExpression(descriptor);
        if (expr == null) return;

        expr.replaceWithExpression(expr.getOperand(), true);
      }
    }

    private static class ReplacePostfixIncWithPrefixFix implements LocalQuickFix {
      private final @IntentionFamilyName String myMessage;

      ReplacePostfixIncWithPrefixFix(GrUnaryExpression expression) {
        myMessage = GroovyBundle.message("replace.postfix.0.with.prefix.0", expression.getOperationToken().getText());
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return myMessage;
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        GrUnaryExpression expr = findUnaryExpression(descriptor);
        if (expr == null) return;

        GrExpression prefix = GroovyPsiElementFactory.getInstance(project)
          .createExpressionFromText(expr.getOperationToken().getText() + expr.getOperand().getText());

        expr.replaceWithExpression(prefix, true);
      }
    }
  }

  @Nullable
  private static GrUnaryExpression findUnaryExpression(ProblemDescriptor descriptor) {
    GrUnaryExpression expr;
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return null;
    PsiElement parent = element.getParent();
    IElementType opType = element.getNode().getElementType();
    if (opType != GroovyTokenTypes.mINC && opType != GroovyTokenTypes.mDEC) return null;
    if (!(parent instanceof GrUnaryExpression)) return null;
    expr = (GrUnaryExpression)parent;
    return expr;
  }
}

