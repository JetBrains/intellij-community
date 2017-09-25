/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.Location;
import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.execution.process.ProcessHandler;
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
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.GradleUrlProvider;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Iterator;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 12/4/2015
 */
public class GradleRunnerUtil {

  /**
   * @deprecated to be removed in the 2018.1 version
   */
  public static DuplexConsoleView attachTaskExecutionView(@NotNull final Project project,
                                                          @NotNull final ConsoleView consoleView,
                                                          final boolean isTaskConsoleEnabledByDefault,
                                                          @Nullable final String stateStorageKey,
                                                          @NotNull final ProcessHandler processHandler,
                                                          @NotNull final ExternalSystemTaskId taskId) {
    final String tripleStateStorageKey = stateStorageKey != null ? stateStorageKey + "_str" : null;
    if (stateStorageKey != null && isTaskConsoleEnabledByDefault && !PropertiesComponent.getInstance().isValueSet(tripleStateStorageKey)) {
      PropertiesComponent.getInstance().setValue(tripleStateStorageKey, Boolean.TRUE.toString());
      PropertiesComponent.getInstance().setValue(stateStorageKey, Boolean.TRUE);
    }

    final TaskExecutionView gradleExecutionConsole = new TaskExecutionView(project);
    final Ref<DuplexConsoleView> duplexConsoleViewRef = Ref.create();
    final DuplexConsoleView duplexConsoleView =
      new DuplexConsoleView<ConsoleView, ConsoleView>(gradleExecutionConsole, consoleView, stateStorageKey) {

        @Override
        public void enableConsole(boolean primary) {
          super.enableConsole(primary);
          if (stateStorageKey != null) {
            PropertiesComponent.getInstance().setValue(tripleStateStorageKey, Boolean.toString(primary));
          }
        }

        @NotNull
        @Override
        public AnAction[] createConsoleActions() {

          final DefaultActionGroup textActionGroup = new DefaultActionGroup() {
            @Override
            public void update(AnActionEvent e) {
              super.update(e);
              if (duplexConsoleViewRef.get() != null) {
                e.getPresentation().setVisible(!duplexConsoleViewRef.get().isPrimaryConsoleEnabled());
              }
            }
          };
          final AnAction[] consoleActions = consoleView.createConsoleActions();
          for (AnAction anAction : consoleActions) {
            textActionGroup.add(anAction);
          }

          final List<AnAction> anActions = ContainerUtil.newArrayList(super.createConsoleActions());
          anActions.add(textActionGroup);
          return ArrayUtil.toObjectArray(anActions, AnAction.class);
        }
      };

    duplexConsoleViewRef.set(duplexConsoleView);

    duplexConsoleView.setDisableSwitchConsoleActionOnProcessEnd(false);
    duplexConsoleView.getSwitchConsoleActionPresentation().setIcon(AllIcons.Actions.ChangeView);
    duplexConsoleView.getSwitchConsoleActionPresentation().setText(GradleBundle.message("gradle.runner.toggle.tree.text.action.name"));

    final ExternalSystemProgressNotificationManager progressManager =
      ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    final ExternalSystemTaskNotificationListenerAdapter taskListener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull final ExternalSystemTaskNotificationEvent event) {
        if (event instanceof ExternalSystemTaskExecutionEvent) {
          UIUtil.invokeLaterIfNeeded(() -> {
            if(((ExternalSystemTaskExecutionEvent)event).getProgressEvent() instanceof ExternalSystemProgressEventUnsupported) {
              duplexConsoleView.enableConsole(false);
            }
            gradleExecutionConsole.onStatusChange((ExternalSystemTaskExecutionEvent)event);
          });
        }
      }

      @Override
      public void onStart(@NotNull ExternalSystemTaskId id, final String workingDir) {
        UIUtil.invokeLaterIfNeeded(() -> gradleExecutionConsole.setWorkingDir(workingDir));
      }

      @Override
      public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull final Exception e) {
        UIUtil.invokeLaterIfNeeded(() -> gradleExecutionConsole.onFailure(e));
      }

      @Override
      public void onEnd(@NotNull ExternalSystemTaskId id) {
        progressManager.removeNotificationListener(this);
      }
    };
    progressManager.addNotificationListener(taskId, taskListener);
    return duplexConsoleView;
  }

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

  @NotNull
  public static String getTestLocationUrl(@Nullable String testName, @NotNull String fqClassName) {
    return testName == null
           ? String.format("%s://%s::%s", GradleUrlProvider.PROTOCOL_ID, GradleUrlProvider.CLASS_PREF, fqClassName)
           : String.format("%s://%s::%s::%s", GradleUrlProvider.PROTOCOL_ID, GradleUrlProvider.METHOD_PREF, fqClassName, testName);
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

      final String className = ((TestOperationDescriptor)descriptor).getClassName();
      if (className == null) return null;

      final String methodName = ((TestOperationDescriptor)descriptor).getMethodName();
      final String testLocationUrl = VirtualFileManager.extractPath(getTestLocationUrl(methodName, className));

      final List<Location> locations = GradleUrlProvider.INSTANCE.getLocation(
        GradleUrlProvider.PROTOCOL_ID, testLocationUrl, project, GlobalSearchScope.allScope(project));
      return ContainerUtil.getFirstItem(locations);
    }
    return getTaskLocation(project, executionInfo);
  }
}
