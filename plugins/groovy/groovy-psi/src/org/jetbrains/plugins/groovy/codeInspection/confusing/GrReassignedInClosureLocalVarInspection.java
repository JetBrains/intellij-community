// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReassignedLocalVarsChecker;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GrReassignedInClosureLocalVarInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        if (!PsiUtil.isLValue(referenceExpression)) return;
        final PsiElement resolved = referenceExpression.resolve();
        if (!PsiUtil.isLocalVariable(resolved)) return;

        final PsiType checked = GrReassignedLocalVarsChecker.getReassignedVarType(referenceExpression, false);
        if (checked == null) return;

        final GrControlFlowOwner varFlowOwner = ControlFlowUtils.findControlFlowOwner(resolved);
        final GrControlFlowOwner refFlorOwner = ControlFlowUtils.findControlFlowOwner(referenceExpression);
        if (isOtherScopeAndType(referenceExpression, checked, varFlowOwner, refFlorOwner)) {
          String flowDescription = getFlowDescription(refFlorOwner);
          final String message = GroovyBundle.message("local.var.0.is.reassigned", ((GrNamedElement)resolved).getName(), flowDescription);
          registerError(referenceExpression, message, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }

  private static boolean isOtherScopeAndType(GrReferenceExpression referenceExpression,
                                             PsiType checked,
                                             GrControlFlowOwner varFlowOwner,
                                             GrControlFlowOwner refFlorOwner) {
    return varFlowOwner != refFlorOwner && !TypesUtil.isAssignable(referenceExpression.getType(), checked, referenceExpression);
  }

  private static String getFlowDescription(GrControlFlowOwner refFlorOwner) {
    String flowDescription;
    if (refFlorOwner instanceof GrClosableBlock) {
      flowDescription = GroovyBundle.message("closure");
    }
    else if (refFlorOwner instanceof GrAnonymousClassDefinition) {
      flowDescription = GroovyBundle.message("anonymous.class");
    }
    else {
      flowDescription = GroovyBundle.message("other.scope");
    }
    return flowDescription;
  }
}
