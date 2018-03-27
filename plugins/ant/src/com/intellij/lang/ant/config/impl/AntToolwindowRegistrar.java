/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

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
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class AntToolwindowRegistrar extends AbstractProjectComponent {
  public AntToolwindowRegistrar(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    final String prefix = AntConfiguration.getActionIdPrefix(myProject);
    final ActionManager actionManager = ActionManager.getInstance();

    for (Keymap keymap : keymapManager.getAllKeymaps()) {
      for (String id : keymap.getActionIdList()) {
        if (id.startsWith(prefix) && actionManager.getAction(id) == null) {
          actionManager.registerAction(id, new TargetActionStub(id, myProject));
        }
      }      
    }
    
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addBeforeTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        final AntConfiguration config = AntConfiguration.getInstance(myProject);
        ((AntConfigurationBase)config).ensureInitialized();
        return config.executeTargetBeforeCompile(createDataContext(context));
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        final AntConfigurationBase config = (AntConfigurationBase)AntConfiguration.getInstance(myProject);
        config.ensureInitialized();
        if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
          final AntBuildTarget target = config.getTargetForEvent(ExecuteAfterCompilationEvent.getInstance());
          if (target != null) {
            context.addMessage(CompilerMessageCategory.INFORMATION, "Skipping ant target \"" + target.getDisplayName() + "\" because of compilation errors", null , -1, -1);
          }
          return true;
        }
        return config.executeTargetAfterCompile(createDataContext(context));
      }
    });
  }

  @NotNull
  private static DataContext createDataContext(CompileContext context) {
    final HashMap<String, Object> dataMap = new HashMap<>();
    final Project project = context.getProject();
    if (project != null) {
      dataMap.put(CommonDataKeys.PROJECT.getName(), project);
    }
    final CompileScope scope = context.getCompileScope();
    final Module[] modules = scope.getAffectedModules();
    if (modules.length == 1) {
      dataMap.put(LangDataKeys.MODULE.getName(), modules[0]);
    }
    dataMap.put(LangDataKeys.MODULE_CONTEXT_ARRAY.getName(), modules);
    dataMap.put("COMPILER_CONTEXT_MAKE", context.isMake());
    return SimpleDataContext.getSimpleContext(dataMap, null);
  }

  @Override
  public void projectClosed() {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(myProject));
    for (String oldId : oldIds) {
      actionManager.unregisterAction(oldId);
    }
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "AntToolwindowRegistrar";
  }
}
