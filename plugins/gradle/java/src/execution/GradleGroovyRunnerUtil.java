// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.Strings.isEmpty;
import static com.intellij.psi.util.InheritanceUtil.isInheritor;
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_DQ;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_SQ;

@ApiStatus.Internal
public final class GradleGroovyRunnerUtil {
  public static @NotNull List<String> getTasksTarget(@NotNull PsiElement element, @Nullable Module module) {
    PsiElement parent = element;
    while (parent.getParent() != null && !(parent.getParent() instanceof PsiFile)) {
      parent = parent.getParent();
    }

    if (parent instanceof GrMethodCallExpression methodCall) {
      String taskName = getTaskNameIfMethodDeclaresIt(methodCall);
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

  private static @Nullable String getTaskNameIfMethodDeclaresIt(GrMethodCallExpression methodCall) {
    String taskNameCandidate = getStringValueFromFirstArg(methodCall);
    if (taskNameCandidate == null) return null;
    PsiMethod resolvedMethod = methodCall.resolveMethod();
    if (resolvedMethod == null) return null;
    PsiClass containingClass = resolvedMethod.getContainingClass();
    if (containingClass == null) return null;

    String methodName = resolvedMethod.getName();
    if (declaresTaskFromTaskContainer(methodName, containingClass)
      || declaresTaskFromTaskCollection(methodName, containingClass)
      || declaresTaskFromProject(methodName, containingClass)
    ) {
      return taskNameCandidate;
    } else {
      return null;
    }
  }

  private static boolean declaresTaskFromTaskContainer(String methodName, PsiClass containingClass) {
    return isInheritor(containingClass, GRADLE_API_TASK_CONTAINER)
           && ("create".equals(methodName) || "register".equals(methodName));
  }

  private static boolean declaresTaskFromTaskCollection(String methodName, PsiClass containingClass) {
    return isInheritor(containingClass, GRADLE_API_TASK_COLLECTION)
           && "named".equals(methodName);
  }

  private static boolean declaresTaskFromProject(String methodName, PsiClass containingClass) {
    return isInheritor(containingClass, GRADLE_API_PROJECT)
           && "task".equals(methodName);
  }

  private static String getStringValueFromFirstArg(GrMethodCallExpression methodCall) {
    final GrExpression[] arguments = methodCall.getExpressionArguments();
    if (arguments.length > 0 && arguments[0] instanceof GrLiteral literalArg
        && literalArg.getValue() instanceof String stringArg
    ) {
      return stringArg;
    } else {
      return null;
    }
  }

  private static @Nullable Module getModule(@NotNull PsiElement element, @NotNull Project project) {
    PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile != null) {
        return ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile);
      }
    }
    return null;
  }

  public static @NotNull List<String> getTasksTarget(@Nullable Location location) {
    if (location == null) return Collections.emptyList();
    if (location instanceof GradleTaskLocation) {
      return ((GradleTaskLocation)location).getTasks();
    }

    Module module = location.getModule();
    return getTasksTarget(location.getPsiElement(), module);
  }

  public static @NotNull List<String> getTasksTarget(@NotNull PsiElement element) {
    return getTasksTarget(element, null);
  }

  @VisibleForTesting
  public static @Nullable String getTaskNameIfContains(PsiElement element) {
    String taskNameCandidate = getTaskNameCandidate(element);
    if (isEmpty(taskNameCandidate)) return null;

    List<String> tasks = getTasksTarget(element);
    if (!tasks.isEmpty() && tasks.contains(taskNameCandidate)) {
      return taskNameCandidate;
    }
    return null;
  }

  private static @Nullable String getTaskNameCandidate(PsiElement element) {
    if (!(element instanceof LeafElement leaf)
        || element instanceof PsiWhiteSpace
        || element instanceof PsiComment
    ) {
      return null;
    }

    if (parentIsReferenceInMethodCall(element)) {
      return element.getText().trim();
    }
    else if (isLiteralArgumentOfMethodCall(element)
             && (leaf.getElementType() == STRING_SQ || leaf.getElementType() == STRING_DQ)
    ) {
      return leaf.getText().substring(1, leaf.getText().length() - 1);
    }
    return null;
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
