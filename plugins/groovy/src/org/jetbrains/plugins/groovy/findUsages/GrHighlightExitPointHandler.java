// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrHighlightExitPointHandler extends HighlightUsagesHandlerBase<PsiElement> {
  private final PsiElement myTarget;

  protected GrHighlightExitPointHandler(Editor editor, PsiFile file, PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  @Override
  public @NotNull List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  @Override
  protected void selectTargets(@NotNull List<? extends PsiElement> targets, @NotNull Consumer<? super List<? extends PsiElement>> selectionConsumer) {
    selectionConsumer.consume(targets);
  }

  @Override
  public void computeUsages(@NotNull List<? extends PsiElement> targets) {
    PsiElement parent = myTarget.getParent();
    if (!(parent instanceof GrReturnStatement) && !(parent instanceof GrThrowStatement)) return;

    final GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(parent);
    ControlFlowUtils.visitAllExitPoints(flowOwner, new ControlFlowUtils.ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        final PsiElement returnElement = instruction.getElement();
        if (returnElement != null && isCorrectReturn(returnElement)) {
          final TextRange range = returnElement.getTextRange();
          myReadUsages.add(range);
        }
        return true;
      }
    });
  }

  private static boolean isCorrectReturn(@Nullable PsiElement e) {
    return e instanceof GrReturnStatement || e instanceof GrThrowStatement || e instanceof GrExpression;
  }

  @Override
  public @Nullable String getFeatureId() {
    return ProductivityFeatureNames.CODEASSISTS_HIGHLIGHT_RETURN;
  }
}
