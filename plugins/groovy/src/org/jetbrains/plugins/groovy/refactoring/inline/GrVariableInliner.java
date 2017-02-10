/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

/**
 * @author Max Medvedev
 */
public class GrVariableInliner implements InlineHandler.Inliner {
  private final GrExpression myTempExpr;

  public GrVariableInliner(GrVariable variable, InlineHandler.Settings settings) {
    GrExpression initializer;
    if (settings instanceof InlineLocalVarSettings) {
      initializer = ((InlineLocalVarSettings)settings).getInitializer();
    }
    else {
      initializer = variable.getInitializerGroovy();
      PsiUtil.LOG.assertTrue(initializer != null);
    }

    myTempExpr = GrIntroduceHandlerBase.insertExplicitCastIfNeeded(variable, initializer);

  }

  @Override
  @Nullable
  public MultiMap<PsiElement, String> getConflicts(@NotNull PsiReference reference, @NotNull PsiElement referenced) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    GrExpression expr = (GrExpression)reference.getElement();
    if (expr.getParent() instanceof GrAssignmentExpression) {
      GrAssignmentExpression parent = (GrAssignmentExpression)expr.getParent();
      if (expr.equals(parent.getLValue())) {
        conflicts.putValue(expr, GroovyRefactoringBundle.message("local.varaible.is.lvalue"));
      }
    }

    if ((referenced instanceof GrAccessorMethod || referenced instanceof GrField) && expr instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)expr).advancedResolve();
      if (resolveResult.getElement() instanceof GrAccessorMethod && !resolveResult.isInvokedOnProperty()) {
        final PsiElement parent = expr.getParent();
        if (!(parent instanceof GrCall && parent instanceof GrExpression)) {
          conflicts.putValue(expr, GroovyRefactoringBundle.message("reference.to.accessor.0.is.used", CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatMethod(
            (GrAccessorMethod)resolveResult.getElement(), PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_TYPE))));
        }
      }
    }

    return conflicts;
  }

  @Override
  public void inlineUsage(@NotNull final UsageInfo usage, @NotNull final PsiElement referenced) {
    inlineReference(usage, referenced, myTempExpr);
  }

  static void inlineReference(UsageInfo usage, PsiElement referenced, GrExpression initializer) {
    if (initializer == null) return;

    GrExpression exprToBeReplaced = (GrExpression)usage.getElement();
    if (exprToBeReplaced == null) return;

    if ((referenced instanceof GrAccessorMethod || referenced instanceof GrField) && exprToBeReplaced instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)exprToBeReplaced).advancedResolve();
      if (resolveResult.getElement() instanceof GrAccessorMethod && !resolveResult.isInvokedOnProperty()) {
        final PsiElement parent = exprToBeReplaced.getParent();
        if (parent instanceof GrCall && parent instanceof GrExpression) {
          exprToBeReplaced = (GrExpression)parent;
        }
        else {
          return;
        }
      }
    }

    GrExpression newExpr = exprToBeReplaced.replaceWithExpression((GrExpression)initializer.copy(), true);
    final Project project = usage.getProject();
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newExpr);
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    GroovyRefactoringUtil.highlightOccurrences(project, editor, new PsiElement[]{newExpr});
    WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }
}
