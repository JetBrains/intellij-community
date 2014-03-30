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

package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class ViewAssertEqualsDiffAction extends AnAction {
  @NonNls public static final String ACTION_ID = "openAssertEqualsDiff";

  public void actionPerformed(final AnActionEvent e) {
    final AbstractTestProxy testProxy = AbstractTestProxy.DATA_KEY.getData(e.getDataContext());
    if (testProxy != null) {
      final AbstractTestProxy.AssertEqualsDiffViewerProvider diffViewerProvider = testProxy.getDiffViewerProvider();
      if (diffViewerProvider != null) {
        diffViewerProvider.openDiff(CommonDataKeys.PROJECT.getData(e.getDataContext()));
      }
    }
  }

  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean enabled;
    final DataContext dataContext = e.getDataContext();
    if (CommonDataKeys.PROJECT.getData(dataContext) == null) {
      enabled = false;
    }
    else {
      final AbstractTestProxy test = AbstractTestProxy.DATA_KEY.getData(dataContext);
      if (test != null) {
        enabled = test.getDiffViewerProvider() != null;
      }
      else {
        enabled = false;
      }
    }
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  public static void registerShortcut(final JComponent component) {
    ActionManager.getInstance().getAction(ACTION_ID).registerCustomShortcutSet(CommonShortcuts.ALT_ENTER, component);
  }
}
