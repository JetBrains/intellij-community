/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 24, 2007
 */
public class AntToolwindowRegistrar extends AbstractProjectComponent {
  public AntToolwindowRegistrar(Project project) {
    super(project);
  }

  public void projectOpened() {
    
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    final String prefix = AntConfiguration.getActionIdPrefix(myProject);
    final ActionManager actionManager = ActionManager.getInstance();

    for (Keymap keymap : keymapManager.getAllKeymaps()) {
      for (String id : keymap.getActionIds()) {
        if (id.startsWith(prefix) && actionManager.getAction(id) == null) {
          actionManager.registerAction(id, new TargetActionStub(id, myProject));
        }
      }      
    }
    
    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final DataContext dataContext = SimpleDataContext.getProjectContext(myProject);
    compilerManager.addBeforeTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        final AntConfiguration config = AntConfiguration.getInstance(myProject);
        ((AntConfigurationBase)config).ensureInitialized();
        return config.executeTargetBeforeCompile(dataContext);
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
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
        return config.executeTargetAfterCompile(dataContext);
      }
    });
  }

  public void projectClosed() {
    final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(myProject));
    for (String oldId : oldIds) {
      actionManager.unregisterAction(oldId);
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "AntToolwindowRegistrar";
  }
}
