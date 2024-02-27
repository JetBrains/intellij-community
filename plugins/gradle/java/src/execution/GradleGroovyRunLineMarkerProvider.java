// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.mapNotNull;
import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.isFromGroovyGradleScript;

/**
 * @author Vladislav.Soroka
 */
public final class GradleGroovyRunLineMarkerProvider extends RunLineMarkerContributor {
  @Nullable
  @Override
  public Info getInfo(@NotNull final PsiElement element) {
    if (!isFromGroovyGradleScript(element)) return null;
    if (element instanceof LeafElement leaf
        && !(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)
        && (parentIsReferenceInMethodCall(element) || isLiteralArgumentOfMethodCall(element))
    ) {
      List<String> tasks = GradleGroovyRunnerUtil.getTasksTarget(element);
      String taskName = getTaskName(leaf);
      if (!tasks.isEmpty() && tasks.contains(taskName)) {
        AnAction[] actions = ExecutorAction.getActions();
        AnActionEvent event = createActionEvent(element);
        return new Info(AllIcons.RunConfigurations.TestState.Run, actions,
                        e -> join(mapNotNull(actions, action -> getText(action, event)), "\n"));
      }
    }
    return null;
  }

  @NotNull
  private static String getTaskName(LeafElement leaf) {
    String text = leaf.getText();
    if (leaf.getElementType() == GroovyElementTypes.STRING_SQ
        || leaf.getElementType() == GroovyElementTypes.STRING_DQ
    ) {
      return text.substring(1, text.length() - 1);
    } else {
      return text.trim();
    }
  }

  private static boolean parentIsReferenceInMethodCall(@NotNull PsiElement element) {
    return element.getParent() instanceof GrReferenceExpression
           && element.getParent().getParent() instanceof GrMethodCallExpression;
  }

  private static boolean isLiteralArgumentOfMethodCall(@NotNull PsiElement element) {
    return element.getParent() instanceof GrLiteral literal
           && literal.getParent() instanceof GrArgumentList argumentList
           && argumentList.getParent() instanceof GrMethodCallExpression;
  }
}