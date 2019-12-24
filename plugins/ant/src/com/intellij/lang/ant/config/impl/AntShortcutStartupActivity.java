// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.actions.TargetActionStub;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

public class AntShortcutStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    final String prefix = AntConfiguration.getActionIdPrefix(project);
    final ActionManager actionManager = ActionManager.getInstance();

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      for (String id : keymap.getActionIdList()) {
        if (id.startsWith(prefix) && actionManager.getAction(id) == null) {
          actionManager.registerAction(id, new TargetActionStub(id, project));
        }
      }
    }

    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
        final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(project));
        for (String oldId : oldIds) {
          actionManager.unregisterAction(oldId);
        }
      }
    });
  }
}
