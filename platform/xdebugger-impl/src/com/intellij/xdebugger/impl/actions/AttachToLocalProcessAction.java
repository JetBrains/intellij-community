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
import com.intellij.execution.process.impl.OSProcessManagerImpl;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.StatusText;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.XLocalAttachDebugger;
import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XLocalAttachGroup;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.InputEvent;
import java.util.*;

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

    ProcessInfo[] processList = OSProcessManagerImpl.getProcessList();
    XLocalAttachDebuggerProvider[] providers = Extensions.getExtensions(XLocalAttachDebuggerProvider.EP);
    
    MyPopupStep step = new MyPopupStep(collectAttachItems(project, processList, providers), project);

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
  public static List<AttachItem> collectAttachItems(@NotNull final Project project, 
                                                    @NotNull ProcessInfo[] processList,
                                                    @NotNull XLocalAttachDebuggerProvider... providers) {
    MultiMap<XLocalAttachGroup, Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>> groupWithItems
      = new MultiMap<XLocalAttachGroup, Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>>();
    
    UserDataHolderBase dataHolder = new UserDataHolderBase();
    for (ProcessInfo eachInfo : processList) {

      MultiMap<XLocalAttachGroup, XLocalAttachDebugger> groupsWithDebuggers = new MultiMap<XLocalAttachGroup, XLocalAttachDebugger>();
      for (XLocalAttachDebuggerProvider eachProvider : providers) {
        groupsWithDebuggers.putValues(eachProvider.getAttachGroup(), eachProvider.getAvailableDebuggers(project, eachInfo, dataHolder));
      }

      for (XLocalAttachGroup eachGroup : groupsWithDebuggers.keySet()) {
        Collection<XLocalAttachDebugger> debuggers = groupsWithDebuggers.get(eachGroup);
        if (!debuggers.isEmpty()) {
          groupWithItems.putValue(eachGroup, Pair.create(eachInfo, new ArrayList<XLocalAttachDebugger>(debuggers)));
        }
      }
    }

    ArrayList<XLocalAttachGroup> sortedGroups = new ArrayList<XLocalAttachGroup>(groupWithItems.keySet());
    Collections.sort(sortedGroups, new Comparator<XLocalAttachGroup>() {
      @Override
      public int compare(XLocalAttachGroup a, XLocalAttachGroup b) {
        return a.getOrder() - b.getOrder();
      }
    });

    List<AttachItem> result = new ArrayList<AttachItem>();
    for (final XLocalAttachGroup eachGroup : sortedGroups) {
      List<Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>> sortedItems
        = new ArrayList<Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>>(groupWithItems.get(eachGroup));
      Collections.sort(sortedItems, new Comparator<Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>>() {
        @Override
        public int compare(Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>> a, Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>> b) {
          return eachGroup.compare(project, a.first, b.first);
        }
      });

      boolean first = true;
      for (Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>> eachItem : sortedItems) {
        result.add(new AttachItem(eachGroup, first, eachItem.first, eachItem.second));
        first = false;
      }
    }

    return result;
  }

  public static class AttachItem {
    @NotNull private final XLocalAttachGroup myGroup;
    private final boolean myIsFirstInGroup;
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final List<XLocalAttachDebugger> myDebuggers;
    @NotNull private final List<AttachItem> mySubItems;

    public AttachItem(@NotNull XLocalAttachGroup group,
                      boolean isFirstInGroup,
                      @NotNull ProcessInfo info,
                      @NotNull List<XLocalAttachDebugger> debuggers) {
      assert !debuggers.isEmpty();

      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myProcessInfo = info;
      myDebuggers = debuggers;
      if (debuggers.size() > 1) {
        mySubItems = ContainerUtil.map(debuggers, new Function<XLocalAttachDebugger, AttachItem>() {
          @Override
          public AttachItem fun(XLocalAttachDebugger debugger) {
            return new AttachItem(myGroup, false, myProcessInfo, Collections.singletonList(debugger));
          }
        });
      }
      else {
        mySubItems = Collections.emptyList();
      }
    }

    @Nullable
    public String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroup.getGroupName() : null;
    }

    @Nullable
    public Icon getIcon(@NotNull Project project) {
      return myGroup.getProcessIcon(project, myProcessInfo);
    }

    @NotNull
    public String getText(@NotNull Project project) {
      return myProcessInfo.getPid() + " " + myGroup.getProcessDisplayText(project, myProcessInfo);
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

    @Nullable
    @Override
    public ListSeparator getSeparatorAbove(AttachItem value) {
      String separatorTitle = value.getSeparatorTitle();
      return separatorTitle == null ? null : new ListSeparator(separatorTitle);
    }

    @Override
    public Icon getIconFor(AttachItem value) {
      return value.getIcon(myProject);
    }

    @NotNull
    @Override
    public String getTextFor(AttachItem value) {
      return value.getText(myProject);
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
