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
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.process.ProcessUtils;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class AttachToLocalProcessAction extends AnAction {
  public AttachToLocalProcessAction() {
    super(XDebuggerBundle.message("xdebugger.attach.toLocal.action"),
          XDebuggerBundle.message("xdebugger.attach.toLocal.action.description"), null);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Project project = getEventProject(e);
    boolean enabled = project != null && Extensions.getExtensions(XLocalAttachDebuggerProvider.EP).length > 0;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    List<AttachItem> items = collectAttachItems(project);
    if (items.isEmpty()) {
      // todo show message
    }

    JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<AttachItem>("Attach", items) {
      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @NotNull
      @Override
      public String getTextFor(AttachItem value) {
        return value.info.getPid() + " " + value.info.getExecutableName();
      }

      @Override
      public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
        startDebugSession(project, selectedValue.debuggers.get(0), selectedValue.info);
        return super.onChosen(selectedValue, finalChoice);
      }
    }).showCenteredInCurrentWindow(project);
  }

  @NotNull
  private static List<AttachItem> collectAttachItems(@NotNull Project project) {
    List<AttachItem> result = new ArrayList<AttachItem>();

    for (ProcessInfo eachInfo : ProcessUtils.getProcessList("")) {
      List<XLocalAttachDebugger> availableDebuggers = new ArrayList<XLocalAttachDebugger>();
      for (XLocalAttachDebuggerProvider eachProvider : Extensions.getExtensions(XLocalAttachDebuggerProvider.EP)) {
        availableDebuggers.addAll(eachProvider.getAvailableDebuggers(project, eachInfo));
      }
      if (!availableDebuggers.isEmpty()) {
        result.add(new AttachItem(eachInfo, availableDebuggers));
      }
    }
    return result;
  }

  private static void startDebugSession(@NotNull Project project,
                                        @NotNull XLocalAttachDebugger debugger,
                                        @NotNull ProcessInfo info) {
    try {
      debugger.attachDebugSession(project, info);
    }
    catch (ExecutionException e) {
      ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, info.getExecutableName(), e);
    }
  }


  public static class AttachItem {
    @NotNull private final ProcessInfo info;
    @NotNull private final List<XLocalAttachDebugger> debuggers;

    public AttachItem(@NotNull ProcessInfo info, @NotNull List<XLocalAttachDebugger> debuggers) {
      this.info = info;
      this.debuggers = debuggers;
    }

    @Nullable
    public Icon getIcon() {
      return null;
    }
  }
}
