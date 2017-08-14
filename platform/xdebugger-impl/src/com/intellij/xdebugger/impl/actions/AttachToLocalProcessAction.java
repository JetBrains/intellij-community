///*
// * Copyright 2000-2016 JetBrains s.r.o.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.intellij.xdebugger.impl.actions;
//
//import com.intellij.execution.ExecutionException;
//import com.intellij.execution.process.OSProcessUtil;
//import com.intellij.execution.process.ProcessInfo;
//import com.intellij.execution.runners.ExecutionUtil;
//import com.intellij.icons.AllIcons;
//import com.intellij.internal.statistic.UsageTrigger;
//import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
//import com.intellij.openapi.actionSystem.AnAction;
//import com.intellij.openapi.actionSystem.AnActionEvent;
//import com.intellij.openapi.application.ApplicationManager;
//import com.intellij.openapi.extensions.Extensions;
//import com.intellij.openapi.progress.PerformInBackgroundOption;
//import com.intellij.openapi.progress.ProgressIndicator;
//import com.intellij.openapi.progress.Task;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.ui.popup.*;
//import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
//import com.intellij.openapi.util.Key;
//import com.intellij.openapi.util.Pair;
//import com.intellij.openapi.util.UserDataHolder;
//import com.intellij.openapi.util.UserDataHolderBase;
//import com.intellij.openapi.util.text.StringUtil;
//import com.intellij.openapi.wm.ToolWindowId;
//import com.intellij.ui.popup.list.ListPopupImpl;
//import com.intellij.util.containers.ContainerUtil;
//import com.intellij.util.containers.MultiMap;
//import com.intellij.util.containers.hash.LinkedHashMap;
//import com.intellij.util.ui.StatusText;
//import com.intellij.xdebugger.XDebuggerBundle;
//import com.intellij.xdebugger.attach.XLocalAttachDebugger;
//import com.intellij.xdebugger.attach.XLocalAttachDebuggerProvider;
//import com.intellij.xdebugger.attach.XLocalAttachGroup;
//import org.intellij.lang.annotations.MagicConstant;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//
//import javax.swing.*;
//import javax.swing.event.ListSelectionEvent;
//import javax.swing.event.ListSelectionListener;
//import java.awt.event.InputEvent;
//import java.util.*;
//
//public class AttachToLocalProcessAction extends AnAction {
//  private static final Key<LinkedHashMap<String, HistoryItem>> HISTORY_KEY = Key.create("AttachToLocalProcessAction.HISTORY_KEY");
//
//  public AttachToLocalProcessAction() {
//    super(XDebuggerBundle.message("xdebugger.attach.toLocal.action"),
//          XDebuggerBundle.message("xdebugger.attach.toLocal.action.description"), AllIcons.Debugger.AttachToProcess);
//  }
//
//  @Override
//  public void update(AnActionEvent e) {
//    super.update(e);
//
//    Project project = getEventProject(e);
//    boolean enabled = project != null && Extensions.getExtensions(XLocalAttachDebuggerProvider.EP).length > 0;
//    e.getPresentation().setEnabledAndVisible(enabled);
//  }
//
//  @Override
//  public void actionPerformed(AnActionEvent e) {
//    final Project project = getEventProject(e);
//    if (project == null) return;
//
//    XLocalAttachDebuggerProvider[] providers = Extensions.getExtensions(XLocalAttachDebuggerProvider.EP);
//
//    new Task.Backgroundable(project, XDebuggerBundle.message("xdebugger.attach.toLocal.action.collectingProcesses"), true, PerformInBackgroundOption.DEAF) {
//      @Override
//      public void run(@NotNull ProgressIndicator indicator) {
//        ProcessInfo[] processList = OSProcessUtil.getProcessList();
//        List<AttachItem> items = collectAttachItems(project, processList, indicator, providers);
//        ApplicationManager.getApplication().invokeLater(() -> {
//          if (project.isDisposed()) {
//            return;
//          }
//          ProcessListStep step = new ProcessListStep(items, project);
//
//          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
//          final JList mainList = ((ListPopupImpl) popup).getList();
//
//          ListSelectionListener listener = event -> {
//            if (event.getValueIsAdjusting()) return;
//
//            Object item = ((JList) event.getSource()).getSelectedValue();
//
//            // if a sub-list is closed, fallback to the selected value from the main list
//            if (item == null) {
//              item = mainList.getSelectedValue();
//            }
//
//            if (item instanceof AttachItem) {
//              String debuggerName = ((AttachItem)item).getSelectedDebugger().getDebuggerDisplayName();
//              debuggerName = StringUtil.shortenTextWithEllipsis(debuggerName, 50, 0);
//              ((ListPopupImpl)popup).setCaption(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.title", debuggerName));
//            }
//          };
//          popup.addListSelectionListener(listener);
//
//          // force first valueChanged event
//          listener.valueChanged(new ListSelectionEvent(mainList, mainList.getMinSelectionIndex(), mainList.getMaxSelectionIndex(), false));
//
//          popup.showCenteredInCurrentWindow(project);
//        });
//      }
//    }.queue();
//  }
//
//  @NotNull
//  public static List<AttachItem> collectAttachItems(@NotNull final Project project,
//                                                    @NotNull ProcessInfo[] processList,
//                                                    @NotNull ProgressIndicator indicator,
//                                                    @NotNull XLocalAttachDebuggerProvider... providers) {
//    MultiMap<XLocalAttachGroup, Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>> groupWithItems = new MultiMap<>();
//
//    UserDataHolderBase dataHolder = new UserDataHolderBase();
//    for (ProcessInfo eachInfo : processList) {
//
//      MultiMap<XLocalAttachGroup, XLocalAttachDebugger> groupsWithDebuggers = new MultiMap<>();
//      for (XLocalAttachDebuggerProvider eachProvider : providers) {
//        indicator.checkCanceled();
//        groupsWithDebuggers.putValues(eachProvider.getAttachGroup(), eachProvider.getAvailableDebuggers(project, eachInfo, dataHolder));
//      }
//
//      for (XLocalAttachGroup eachGroup : groupsWithDebuggers.keySet()) {
//        Collection<XLocalAttachDebugger> debuggers = groupsWithDebuggers.get(eachGroup);
//        if (!debuggers.isEmpty()) {
//          groupWithItems.putValue(eachGroup, Pair.create(eachInfo, new ArrayList<>(debuggers)));
//        }
//      }
//    }
//
//    ArrayList<XLocalAttachGroup> sortedGroups = new ArrayList<>(groupWithItems.keySet());
//    sortedGroups.sort(Comparator.comparingInt(XLocalAttachGroup::getOrder));
//
//    List<AttachItem> currentItems = new ArrayList<>();
//    for (final XLocalAttachGroup eachGroup : sortedGroups) {
//      List<Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>>> sortedItems
//          = new ArrayList<>(groupWithItems.get(eachGroup));
//      sortedItems.sort((a, b) -> eachGroup.compare(project, a.first, b.first, dataHolder));
//
//      boolean first = true;
//      for (Pair<ProcessInfo, ArrayList<XLocalAttachDebugger>> eachItem : sortedItems) {
//        currentItems.add(new AttachItem(eachGroup, first, eachItem.first, eachItem.second, dataHolder));
//        first = false;
//      }
//    }
//
//    List<AttachItem> currentHistoryItems = new ArrayList<>();
//    List<HistoryItem> history = getHistory(project);
//    for (int i = history.size() - 1; i >= 0; i--) {
//      HistoryItem eachHistoryItem = history.get(i);
//      for (AttachItem eachCurrentItem : currentItems) {
//        boolean isSuitableItem = eachHistoryItem.getGroup().equals(eachCurrentItem.getGroup()) &&
//            eachHistoryItem.getInfo().getCommandLine().equals(eachCurrentItem.getInfo().getCommandLine());
//        if (!isSuitableItem) continue;
//
//        List<XLocalAttachDebugger> debuggers = eachCurrentItem.getDebuggers();
//        int selectedDebugger = -1;
//        for (int j = 0; j < debuggers.size(); j++) {
//          XLocalAttachDebugger eachDebugger = debuggers.get(j);
//          if (eachDebugger.getDebuggerDisplayName().equals(eachHistoryItem.getDebuggerName())) {
//            selectedDebugger = j;
//            break;
//          }
//        }
//        if (selectedDebugger == -1) continue;
//
//        currentHistoryItems.add(new AttachItem(eachCurrentItem.getGroup(),
//            currentHistoryItems.isEmpty(),
//            XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent"),
//            eachCurrentItem.getInfo(),
//            debuggers,
//            selectedDebugger,
//            dataHolder));
//      }
//    }
//
//    currentHistoryItems.addAll(currentItems);
//    return currentHistoryItems;
//  }
//
//  public static void addToHistory(@NotNull Project project, @NotNull AttachItem item) {
//    LinkedHashMap<String, HistoryItem> history = project.getUserData(HISTORY_KEY);
//    if (history == null) {
//      project.putUserData(HISTORY_KEY, history = new LinkedHashMap<>());
//    }
//    ProcessInfo processInfo = item.getInfo();
//    history.remove(processInfo.getCommandLine());
//    history.put(processInfo.getCommandLine(), new HistoryItem(processInfo, item.getGroup(),
//                                                              item.getSelectedDebugger().getDebuggerDisplayName()));
//    while (history.size() > 4) {
//      history.remove(history.keySet().iterator().next());
//    }
//  }
//
//  @NotNull
//  public static List<HistoryItem> getHistory(@NotNull Project project) {
//    LinkedHashMap<String, HistoryItem> history = project.getUserData(HISTORY_KEY);
//    return history == null ? Collections.emptyList()
//                           : Collections.unmodifiableList(new ArrayList<>(history.values()));
//  }
//
//  public static class HistoryItem {
//    @NotNull private final ProcessInfo myProcessInfo;
//    @NotNull private final XLocalAttachGroup myGroup;
//    @NotNull private final String myDebuggerName;
//
//    public HistoryItem(@NotNull ProcessInfo processInfo,
//                       @NotNull XLocalAttachGroup group,
//                       @NotNull String debuggerName) {
//      myProcessInfo = processInfo;
//      myGroup = group;
//      myDebuggerName = debuggerName;
//    }
//
//    @NotNull
//    public ProcessInfo getInfo() {
//      return myProcessInfo;
//    }
//
//    @NotNull
//    public XLocalAttachGroup getGroup() {
//      return myGroup;
//    }
//
//    @NotNull
//    public String getDebuggerName() {
//      return myDebuggerName;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//      if (this == o) return true;
//      if (o == null || getClass() != o.getClass()) return false;
//
//      HistoryItem item = (HistoryItem)o;
//
//      if (!myProcessInfo.equals(item.myProcessInfo)) return false;
//      if (!myGroup.equals(item.myGroup)) return false;
//      if (!myDebuggerName.equals(item.myDebuggerName)) return false;
//
//      return true;
//    }
//
//    @Override
//    public int hashCode() {
//      int result = myProcessInfo.hashCode();
//      result = 31 * result + myGroup.hashCode();
//      result = 31 * result + myDebuggerName.hashCode();
//      return result;
//    }
//  }
//
//  public static class AttachItem {
//    @NotNull private final XLocalAttachGroup myGroup;
//    private final boolean myIsFirstInGroup;
//    @NotNull private final String myGroupName;
//    @NotNull private UserDataHolder myDataHolder;
//    @NotNull private final ProcessInfo myProcessInfo;
//    @NotNull private final List<XLocalAttachDebugger> myDebuggers;
//    private final int mySelectedDebugger;
//    @NotNull private final List<AttachItem> mySubItems;
//
//    public AttachItem(@NotNull XLocalAttachGroup group,
//                      boolean isFirstInGroup,
//                      @NotNull ProcessInfo info,
//                      @NotNull List<XLocalAttachDebugger> debuggers,
//                      @NotNull UserDataHolder dataHolder) {
//      this(group, isFirstInGroup, group.getGroupName(), info, debuggers, 0, dataHolder);
//    }
//
//    public AttachItem(@NotNull XLocalAttachGroup group,
//                      boolean isFirstInGroup,
//                      @NotNull String groupName,
//                      @NotNull ProcessInfo info,
//                      @NotNull List<XLocalAttachDebugger> debuggers,
//                      int selectedDebugger,
//                      @NotNull UserDataHolder dataHolder) {
//      myGroupName = groupName;
//      myDataHolder = dataHolder;
//      assert !debuggers.isEmpty() : "debugger list should not be empty";
//      assert selectedDebugger >= 0 && selectedDebugger < debuggers.size() : "wrong selected debugger index";
//
//      myGroup = group;
//      myIsFirstInGroup = isFirstInGroup;
//      myProcessInfo = info;
//      myDebuggers = debuggers;
//      mySelectedDebugger = selectedDebugger;
//
//      if (debuggers.size() > 1) {
//        mySubItems = ContainerUtil.map(debuggers, debugger -> new AttachItem(myGroup, false, myProcessInfo, Collections.singletonList(debugger), dataHolder));
//      }
//      else {
//        mySubItems = Collections.emptyList();
//      }
//    }
//
//    @NotNull
//    public ProcessInfo getInfo() {
//      return myProcessInfo;
//    }
//
//    @NotNull
//    public XLocalAttachGroup getGroup() {
//      return myGroup;
//    }
//
//    @Nullable
//    public String getSeparatorTitle() {
//      return myIsFirstInGroup ? myGroupName : null;
//    }
//
//    @Nullable
//    public Icon getIcon(@NotNull Project project) {
//      return myGroup.getProcessIcon(project, myProcessInfo, myDataHolder);
//    }
//
//    @NotNull
//    public String getText(@NotNull Project project) {
//      String shortenedText = StringUtil.shortenTextWithEllipsis(myGroup.getProcessDisplayText(project, myProcessInfo, myDataHolder), 200, 0);
//      return myProcessInfo.getPid() + " " + shortenedText;
//    }
//
//    @NotNull
//    public List<XLocalAttachDebugger> getDebuggers() {
//      return myDebuggers;
//    }
//
//    @NotNull
//    public XLocalAttachDebugger getSelectedDebugger() {
//      return myDebuggers.get(mySelectedDebugger);
//    }
//
//
//    public void startDebugSession(@NotNull Project project) {
//      XLocalAttachDebugger debugger = getSelectedDebugger();
//      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach.local"));
//      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach.local." + debugger.getDebuggerDisplayName()));
//
//      try {
//        debugger.attachDebugSession(project, myProcessInfo);
//      }
//      catch (ExecutionException e) {
//        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, myProcessInfo.getText(), e);
//      }
//    }
//  }
//
//  private static class MyBasePopupStep extends BaseListPopupStep<AttachItem> {
//    @NotNull final Project myProject;
//
//    public MyBasePopupStep(@NotNull Project project,
//                           @Nullable String title,
//                           List<? extends AttachItem> values) {
//      super(title, values);
//      myProject = project;
//    }
//
//    @Override
//    public boolean isSpeedSearchEnabled() {
//      return true;
//    }
//
//    @Override
//    public boolean isAutoSelectionEnabled() {
//      return false;
//    }
//
//    @Override
//    public boolean hasSubstep(AttachItem selectedValue) {
//      return !selectedValue.getSubItems().isEmpty();
//    }
//
//    @Override
//    public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
//      addToHistory(myProject, selectedValue);
//      return doFinalStep(() -> selectedValue.startDebugSession(myProject));
//    }
//  }
//
//  private static class ProcessListStep extends MyBasePopupStep implements ListPopupStepEx<AttachItem> {
//    public ProcessListStep(@NotNull List<AttachItem> items, @NotNull Project project) {
//      super(project, XDebuggerBundle.message("xdebugger.attach.toLocal.popup.title.default"), items);
//    }
//
//    @Nullable
//    @Override
//    public ListSeparator getSeparatorAbove(AttachItem value) {
//      String separatorTitle = value.getSeparatorTitle();
//      return separatorTitle == null ? null : new ListSeparator(separatorTitle);
//    }
//
//    @Override
//    public Icon getIconFor(AttachItem value) {
//      return value.getIcon(myProject);
//    }
//
//    @NotNull
//    @Override
//    public String getTextFor(AttachItem value) {
//      return value.getText(myProject);
//    }
//
//    @Nullable
//    @Override
//    public String getTooltipTextFor(AttachItem value) {
//      return value.getText(myProject);
//    }
//
//    @Override
//    public void setEmptyText(@NotNull StatusText emptyText) {
//      emptyText.setText(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.emptyText"));
//    }
//
//    @Override
//    public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
//      if (finalChoice) {
//        return super.onChosen(selectedValue, true);
//      }
//      return new DebuggerListStep(selectedValue.getSubItems(), selectedValue.mySelectedDebugger);
//    }
//
//    @Override
//    public PopupStep onChosen(AttachItem selectedValue,
//                              boolean finalChoice,
//                              @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers) {
//      return onChosen(selectedValue, finalChoice);
//    }
//
//    private class DebuggerListStep extends MyBasePopupStep {
//      public DebuggerListStep(List<AttachItem> items, int selectedItem) {
//        super(ProcessListStep.this.myProject,
//              XDebuggerBundle.message("xdebugger.attach.toLocal.popup.selectDebugger.title"), items);
//        setDefaultOptionIndex(selectedItem);
//      }
//
//      @NotNull
//      @Override
//      public String getTextFor(AttachItem value) {
//        return value.getSelectedDebugger().getDebuggerDisplayName();
//      }
//    }
//  }
//}
