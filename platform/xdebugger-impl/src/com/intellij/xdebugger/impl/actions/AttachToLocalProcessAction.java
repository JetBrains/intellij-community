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
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStepEx;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StatusText;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
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

    MyPopupStep step = new MyPopupStep(collectAttachItems(project), project);

    final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
    final JList mainList = ((ListPopupImpl)popup).getList();

    ListSelectionListener listener = new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) return;

        Object item = ((JList)event.getSource()).getSelectedValue();

        // if a sub-list is closed, fallback to the selected value from the main list 
        if (item == null) {
          item = mainList.getSelectedValue();
        }

        if (item instanceof AttachItem) {
          popup.setAdText(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.adText",
                                                  ((AttachItem)item).getSelectedDebugger().getDebuggerDisplayName()),
                          SwingConstants.LEADING);
        }
      }
    };
    popup.addListSelectionListener(listener);

    // force first valueChanged event
    listener.valueChanged(new ListSelectionEvent(mainList, mainList.getMinSelectionIndex(), mainList.getMaxSelectionIndex(), false));

    popup.showCenteredInCurrentWindow(project);
  }

  @NotNull
  private static List<AttachItem> collectAttachItems(@NotNull Project project) {
    List<AttachItem> result = new ArrayList<AttachItem>();

    for (ProcessInfo eachInfo : ProcessUtils.getProcessList()) {
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

  public static class AttachItem {
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final List<XLocalAttachDebugger> myDebuggers;
    @NotNull private final List<AttachItem> mySubItems;

    public AttachItem(@NotNull ProcessInfo info, @NotNull List<XLocalAttachDebugger> debuggers) {
      assert !debuggers.isEmpty();

      this.myProcessInfo = info;
      this.myDebuggers = debuggers;
      if (debuggers.size() > 1) {
        mySubItems = ContainerUtil.map(debuggers, new Function<XLocalAttachDebugger, AttachItem>() {
          @Override
          public AttachItem fun(XLocalAttachDebugger debugger) {
            return new AttachItem(myProcessInfo, Collections.singletonList(debugger));
          }
        });
      }
      else {
        mySubItems = Collections.emptyList();
      }
    }

    @Nullable
    public Icon getIcon() {
      return null;
    }

    @NotNull
    public XLocalAttachDebugger getSelectedDebugger() {
      return myDebuggers.get(0);
    }

    @NotNull
    public List<AttachItem> getSubItems() {
      return mySubItems;
    }

    public void startDebugSession(@NotNull Project project) {
      try {
        getSelectedDebugger().attachDebugSession(project, myProcessInfo);
      }
      catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, myProcessInfo.getExecutableName(), e);
      }
    }
  }

  private static class MyPopupStep extends BaseListPopupStep<AttachItem> implements ListPopupStepEx<AttachItem> {
    @NotNull private final Project myProject;

    public MyPopupStep(@NotNull List<AttachItem> items, @NotNull Project project) {
      super(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.title"), items);
      myProject = project;
    }

    @Override
    public boolean isSpeedSearchEnabled() {
      return true;
    }

    @Override
    public boolean isAutoSelectionEnabled() {
      return false;
    }

    @NotNull
    @Override
    public String getTextFor(AttachItem value) {
      return value.myProcessInfo.getPid() + " " + value.myProcessInfo.getExecutableName();
    }

    @Override
    public boolean hasSubstep(AttachItem selectedValue) {
      return !selectedValue.getSubItems().isEmpty();
    }

    @Nullable
    @Override
    public String getTooltipTextFor(AttachItem value) {
      return null;
    }

    @Override
    public void setEmptyText(@NotNull StatusText emptyText) {
      emptyText.setText(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.emptyText"));
    }


    @Override
    public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
      if (finalChoice) {
        selectedValue.startDebugSession(myProject);
        return FINAL_CHOICE;
      }

      return new BaseListPopupStep<AttachItem>(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.selectDebugger.title"),
                                               selectedValue.getSubItems()) {
        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @NotNull
        @Override
        public String getTextFor(AttachItem value) {
          return value.getSelectedDebugger().getDebuggerDisplayName();
        }

        @Override
        public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
          selectedValue.startDebugSession(myProject);
          return FINAL_CHOICE;
        }
      };
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue,
                              boolean finalChoice,
                              @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers) {
      return onChosen(selectedValue, finalChoice);
    }
  }
}
