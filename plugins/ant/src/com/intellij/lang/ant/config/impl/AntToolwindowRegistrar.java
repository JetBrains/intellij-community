// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.lang.ant.config.actions.TargetActionStub;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * @author Eugene Zhuravlev
 */
final class AntToolwindowRegistrar implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        AntToolwindowRegistrar.projectOpened(project);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        AntToolwindowRegistrar.projectClosed(project);
      }
    });
  }

  private static void projectOpened(@NotNull Project project) {
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    final String prefix = AntConfiguration.getActionIdPrefix(project);
    final ActionManager actionManager = ActionManager.getInstance();

    for (Keymap keymap : keymapManager.getAllKeymaps()) {
      for (String id : keymap.getActionIdList()) {
        if (id.startsWith(prefix) && actionManager.getAction(id) == null) {
          actionManager.registerAction(id, new TargetActionStub(id, project));
        }
      }
    }

    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.addBeforeTask(new CompileTask() {
      @Override
      public boolean execute(@NotNull CompileContext context) {
        return initializeAndRun(project, context, antConfiguration -> antConfiguration.executeTargetBeforeCompile(createDataContext(context)));
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      @Override
      public boolean execute(@NotNull CompileContext context) {
        return initializeAndRun(project, context, antConfiguration -> {
          if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
            final AntBuildTarget target = antConfiguration.getTargetForEvent(ExecuteAfterCompilationEvent.getInstance());
            if (target != null) {
              context.addMessage(CompilerMessageCategory.INFORMATION,
                                 "Skipping ant target \"" + target.getDisplayName() + "\" because of compilation errors", null, -1, -1);
            }
            return true;
          }
          return antConfiguration.executeTargetAfterCompile(createDataContext(context));
        });
      }
    });
  }

  private static boolean initializeAndRun(@NotNull Project project, @NotNull CompileContext context, Processor<AntConfigurationBase> action) {
    context.getProgressIndicator().pushState();
    try {
      context.getProgressIndicator().setText(AntBundle.message("loading.ant.config.progress"));
      AntConfigurationBase config = AntConfigurationBase.getInstance(project);
      config.ensureInitialized();
      context.getProgressIndicator().setText("Running Ant Tasks...");
      return action.process(config);
    }
    finally {
      context.getProgressIndicator().popState();
    }
  }

  @NotNull
  private static DataContext createDataContext(@NotNull CompileContext context) {
    final HashMap<String, Object> dataMap = new HashMap<>();
    final Project project = context.getProject();
    dataMap.put(CommonDataKeys.PROJECT.getName(), project);
    final CompileScope scope = context.getCompileScope();
    final Module[] modules = scope.getAffectedModules();
    if (modules.length == 1) {
      dataMap.put(LangDataKeys.MODULE.getName(), modules[0]);
    }
    dataMap.put(LangDataKeys.MODULE_CONTEXT_ARRAY.getName(), modules);
    dataMap.put("COMPILER_CONTEXT_MAKE", context.isMake());
    return SimpleDataContext.getSimpleContext(dataMap, null);
  }

  private static void projectClosed(@NotNull Project project) {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    for (String oldId : actionManager.getActionIds(AntConfiguration.getActionIdPrefix(project))) {
      actionManager.unregisterAction(oldId);
    }
  }
}
