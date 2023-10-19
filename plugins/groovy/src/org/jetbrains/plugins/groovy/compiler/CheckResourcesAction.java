// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.groovy.GroovyResourceChecker;

public abstract class CheckResourcesAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    checkResources(e.getProject(), null, this instanceof Rebuild);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @VisibleForTesting
  public static void checkResources(Project project, @Nullable CompileStatusNotification callback, boolean rebuild) {
    ProjectCompileScope scope = new ProjectCompileScope(project);
    scope.putUserData(GroovyResourceChecker.CHECKING_RESOURCES_REBUILD, rebuild);
    CompilerManager.getInstance(project).make(scope, callback);
  }

  public static class Make extends CheckResourcesAction { }
  public static class Rebuild extends CheckResourcesAction { }
}
