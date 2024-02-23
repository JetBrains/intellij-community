// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

public final class GradleGroovyRunnerUtil {
  @NotNull
  public static List<String> getTasksTarget(@NotNull PsiElement element, @Nullable Module module) {
    PsiElement parent = element;
    while (parent.getParent() != null && !(parent.getParent() instanceof PsiFile)) {
      parent = parent.getParent();
    }

    if (parent instanceof GrMethodCallExpression methodCall
        && isTaskDeclarationMethod(methodCall)
    ) {
      String taskName = getStringArgument(methodCall);
      if (taskName != null) return Collections.singletonList(taskName);
    } else if (parent instanceof GrApplicationStatement) {
      PsiElement shiftExpression = parent.getChildren()[1].getChildren()[0];
      if (GradleResolverUtil.isLShiftElement(shiftExpression)) {
        PsiElement shiftiesChild = shiftExpression.getChildren()[0];
        if (shiftiesChild instanceof GrReferenceExpression) {
          return Collections.singletonList(shiftiesChild.getText());
        }
        else if (shiftiesChild instanceof GrMethodCallExpression) {
          return Collections.singletonList(shiftiesChild.getChildren()[0].getText());
        }
      }
      else if (shiftExpression instanceof GrMethodCallExpression) {
        return Collections.singletonList(shiftExpression.getChildren()[0].getText());
      }
    }
    GrMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(element, GrMethodCallExpression.class);
    if (methodCallExpression != null) {
      String taskNameCandidate = methodCallExpression.getChildren()[0].getText();
      Project project = element.getProject();
      if (module == null) {
        module = getModule(element, project);
      }
      GradleExtensionsSettings.GradleExtensionsData extensionsData = GradleExtensionsSettings.getInstance(project).getExtensionsFor(module);
      if (extensionsData != null) {
        GradleExtensionsSettings.GradleTask gradleTask = extensionsData.tasksMap.get(taskNameCandidate);
        if (gradleTask != null) {
          return Collections.singletonList(taskNameCandidate);
        }
      }
    }

    return Collections.emptyList();
  }

  private static boolean isTaskDeclarationMethod(GrMethodCallExpression methodCall) {
    return PsiUtil.isMethodCall(methodCall, "createTask")
           || isTaskContainerMethodToDeclare(methodCall)
           || isTaskMethodFromProject(methodCall);
  }

  @Nullable
  private static String getStringArgument(GrMethodCallExpression methodCall) {
    final GrExpression[] arguments = methodCall.getExpressionArguments();
    if (arguments.length > 0 && arguments[0] instanceof GrLiteral literalArg
        && literalArg.getValue() instanceof String stringArg
    ) {
      return stringArg;
    } else {
      return null;
    }
  }

  @Nullable
  private static Module getModule(@NotNull PsiElement element, @NotNull Project project) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile != null) {
        return ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile);
      }
    }
    return null;
  }

  private static boolean isTaskContainerMethodToDeclare(GrMethodCallExpression methodCall) {
    PsiElement lastMethod = PsiTreeUtil.lastChild(methodCall.getInvokedExpression());
    if (!"create".equals(lastMethod.getText()) && !"register".equals(lastMethod.getText())) {
      return false;
    }
    PsiElement maybeTaskContainer = getPrevSiblingSkipDots(lastMethod);
    if (maybeTaskContainer instanceof GrMethodCallExpression maybeGetTasksCall) {
      PsiElement maybeGetTasks = maybeGetTasksCall.getInvokedExpression().getLastChild();
      return "getTasks".equals(maybeGetTasks.getText());
    } else if (maybeTaskContainer instanceof GrReferenceExpression maybeTasks) {
      return "tasks".equals(maybeTasks.getLastChild().getText());
    }
    return false;
  }

  private static boolean isTaskMethodFromProject(GrMethodCallExpression methodCall) {
    PsiElement lastMethod = PsiTreeUtil.lastChild(methodCall.getInvokedExpression());
    if (!"task".equals(lastMethod.getText())) return false;

    PsiElement maybeProject = getPrevSiblingSkipDots(lastMethod);
    if (maybeProject instanceof GrMethodCallExpression maybeGetProjectCall) {
      PsiElement maybeGetProject = maybeGetProjectCall.getInvokedExpression().getLastChild();
      return "getProject".equals(maybeGetProject.getText());
    } else if (maybeProject instanceof GrReferenceExpression) {
      return "project".equals(maybeProject.getText());
    }
    return false;
  }

  @Nullable
  private static PsiElement getPrevSiblingSkipDots(PsiElement lastMethod) {
    return PsiUtil.skipSet(lastMethod, false, TokenSet.orSet(TokenSets.WHITE_SPACES_SET, TokenSets.DOTS));
  }

  @NotNull
  public static List<String> getTasksTarget(@Nullable Location location) {
    if (location == null) return Collections.emptyList();
    if (location instanceof GradleTaskLocation) {
      return ((GradleTaskLocation)location).getTasks();
    }

    Module module = location.getModule();
    return getTasksTarget(location.getPsiElement(), module);
  }

  @NotNull
  public static List<String> getTasksTarget(@NotNull PsiElement element) {
    return getTasksTarget(element, null);
  }
}
