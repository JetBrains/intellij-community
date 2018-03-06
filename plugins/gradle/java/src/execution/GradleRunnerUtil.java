// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskLocation;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testIntegration.TestLocator;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Iterator;
import java.util.List;

import static com.intellij.util.io.URLUtil.SCHEME_SEPARATOR;

/**
 * @author Vladislav.Soroka
 * @since 12/4/2015
 */
public class GradleRunnerUtil {

  @Nullable
  public static Location<PsiMethod> getMethodLocation(@NotNull Location contextLocation) {
    Location<PsiMethod> methodLocation = getTestMethod(contextLocation);
    if (methodLocation == null) return null;

    if (contextLocation instanceof PsiMemberParameterizedLocation) {
      PsiClass containingClass = ((PsiMemberParameterizedLocation)contextLocation).getContainingClass();
      if (containingClass != null) {
        methodLocation = MethodLocation.elementInClass(methodLocation.getPsiElement(), containingClass);
      }
    }
    return methodLocation;
  }

  @Nullable
  public static Location<PsiMethod> getTestMethod(final Location<?> location) {
    for (Iterator<Location<PsiMethod>> iterator = location.getAncestors(PsiMethod.class, false); iterator.hasNext(); ) {
      final Location<PsiMethod> methodLocation = iterator.next();
      if (JUnitUtil.isTestMethod(methodLocation, false)) return methodLocation;
    }
    return null;
  }

  /**
   * @deprecated to be removed in 2018.2
   */
  @NotNull
  public static String getTestLocationUrl(@Nullable String testName, @NotNull String fqClassName) {
    return testName == null
           ? JavaTestLocator.TEST_PROTOCOL + SCHEME_SEPARATOR + fqClassName
           : JavaTestLocator.TEST_PROTOCOL + SCHEME_SEPARATOR + StringUtil.getQualifiedName(fqClassName, testName);
  }

  public static Object getData(@NotNull Project project, @NonNls String dataId, @NotNull ExecutionInfo executionInfo) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      final Location location = getLocation(project, executionInfo);
      final OpenFileDescriptor openFileDescriptor = location == null ? null : location.getOpenFileDescriptor();
      if (openFileDescriptor != null && openFileDescriptor.getFile().isValid()) {
        return openFileDescriptor;
      }
    }
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      final Location location = getLocation(project, executionInfo);
      if (location != null) {
        final PsiElement element = location.getPsiElement();
        return element.isValid() ? element : null;
      }
      else {
        return null;
      }
    }
    if (Location.DATA_KEY.is(dataId)) return getLocation(project, executionInfo);
    return null;
  }

  @Nullable
  public static ExternalSystemTaskLocation getTaskLocation(Project project, ExecutionInfo... executionInfos) {
    ExternalTaskExecutionInfo taskExecutionInfo = new ExternalTaskExecutionInfo();

    String projectPath = null;
    final List<String> taskNames = taskExecutionInfo.getSettings().getTaskNames();
    for (ExecutionInfo executionInfo : executionInfos) {
      final OperationDescriptor descriptor = executionInfo.getDescriptor();
      if (descriptor instanceof TaskOperationDescriptor) {
        final String taskName = ((TaskOperationDescriptor)descriptor).getTaskName();
        if (projectPath == null) {
          projectPath = executionInfo.getWorkingDir();
        }
        else if (!projectPath.equals(executionInfo.getWorkingDir())) {
          return null;
        }
        taskNames.add(taskName);
      }
      else {
        return null;
      }
    }

    if (!taskNames.isEmpty()) {
      taskExecutionInfo.getSettings().setExternalSystemIdString(GradleConstants.SYSTEM_ID.toString());
      taskExecutionInfo.getSettings().setExternalProjectPath(projectPath);
      return ExternalSystemTaskLocation.create(project, GradleConstants.SYSTEM_ID, projectPath, taskExecutionInfo);
    }
    return null;
  }

  @Nullable
  private static Location getLocation(@NotNull Project project, @NotNull ExecutionInfo executionInfo) {
    final OperationDescriptor descriptor = executionInfo.getDescriptor();
    if (descriptor instanceof TestOperationDescriptor) {
      if (DumbService.isDumb(project)) return null;

      String suiteName = ((TestOperationDescriptor)descriptor).getSuiteName();
      if (StringUtil.isNotEmpty(suiteName)) {
        return TestLocator.getLocation(JavaTestLocator.SUITE_PROTOCOL + SCHEME_SEPARATOR + suiteName, project);
      }

      final String className = ((TestOperationDescriptor)descriptor).getClassName();
      if (className == null) return null;

      final String methodName = ((TestOperationDescriptor)descriptor).getMethodName();
      return TestLocator.getLocation(
        JavaTestLocator.TEST_PROTOCOL + SCHEME_SEPARATOR + StringUtil.getQualifiedName(className, methodName), project);
    }
    return getTaskLocation(project, executionInfo);
  }
}
