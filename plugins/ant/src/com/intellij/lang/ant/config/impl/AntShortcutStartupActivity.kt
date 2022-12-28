// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntDisposable;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.actions.TargetActionStub;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

final class AntShortcutStartupActivity implements StartupActivity {
  @Override
  public void runActivity(@NotNull Project project) {
    Disposable activityDisposable = ExtensionPointUtil.createExtensionDisposable(this, StartupActivity.POST_STARTUP_ACTIVITY);
    Disposer.register(AntDisposable.getInstance(project), activityDisposable);

    final String prefix = AntConfiguration.getActionIdPrefix(project);
    final ActionManager actionManager = ActionManager.getInstance();

    for (Keymap keymap : KeymapManagerEx.getInstanceEx().getAllKeymaps()) {
      for (String id : keymap.getActionIdList()) {
        if (id.startsWith(prefix) && actionManager.getAction(id) == null) {
          actionManager.registerAction(id, new TargetActionStub(id, project));
        }
      }
    }

    Disposer.register(activityDisposable, () -> unregisterAction(project));
  }

  private static void unregisterAction(@NotNull Project project) {
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    for (String oldId : actionManager.getActionIdList(AntConfiguration.getActionIdPrefix(project))) {
      actionManager.unregisterAction(oldId);
    }
  }
}
