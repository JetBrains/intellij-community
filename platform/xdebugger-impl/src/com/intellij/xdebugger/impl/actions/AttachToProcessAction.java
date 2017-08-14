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
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.remote.RemoteSdkCredentials;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.StatusText;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.InputEvent;
import java.util.*;

public class AttachToProcessAction extends AnAction {
  private static final Key<LinkedHashMap<String, HistoryItem>> HISTORY_KEY = Key.create("AttachToProcessAction.HISTORY_KEY");

  public AttachToProcessAction() {
    super(XDebuggerBundle.message("xdebugger.attach.action"),
          XDebuggerBundle.message("xdebugger.attach.action.description"), AllIcons.Debugger.AttachToProcess);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Project project = getEventProject(e);
    int extensionSize =
      Extensions.getExtensions(XLocalAttachDebuggerProvider.EP).length + Extensions.getExtensions(XRemoteProcessListProvider.EP).length;
    boolean enabled = project != null && extensionSize > 0;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    XLocalAttachDebuggerProvider[] localAttachProviders = Extensions.getExtensions(XLocalAttachDebuggerProvider.EP);
    XRemoteAttachDebuggerProvider[] remoteAttachProviders = Extensions.getExtensions(XRemoteAttachDebuggerProvider.EP);
    XRemoteProcessListProvider[] remotes = Extensions.getExtensions(XRemoteProcessListProvider.EP);

    new Task.Backgroundable(project, XDebuggerBundle.message("xdebugger.attach.action.collectingItems"), true,
                            PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        ProcessInfo[] processList = OSProcessUtil.getProcessList();
        List<AttachToLocalProcessItem> localAttachItems = collectAttachItems(project, processList, indicator, localAttachProviders);
        List<RemoteAttachItem> remoteAttachItems = collectRemotes(project, remotes, remoteAttachProviders);

        List<AttachItem> attachItems = new ArrayList<>(remoteAttachItems);
        attachItems.addAll(localAttachItems);

        ApplicationManager.getApplication().invokeLater(() -> {
          if (project.isDisposed()) {
            return;
          }
          AttachListStep step = new AttachListStep(attachItems, project);

          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
          final JList mainList = ((ListPopupImpl)popup).getList();

          ListSelectionListener listener = event -> {
            if (event.getValueIsAdjusting()) return;

            Object item = ((JList)event.getSource()).getSelectedValue();

            // if a sub-list is closed, fallback to the selected value from the main list
            if (item == null) {
              item = mainList.getSelectedValue();
            }

            if (item instanceof AttachToLocalProcessItem) {
              String debuggerName = ((AttachToLocalProcessItem)item).getSelectedDebugger().getDebuggerDisplayName();
              debuggerName = StringUtil.shortenTextWithEllipsis(debuggerName, 50, 0);
              ((ListPopupImpl)popup).setCaption(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.title", debuggerName));
            }

            if (item instanceof RemoteAttachItem) {
              String remoteName = ((RemoteAttachItem)item).getText(project);
              ((ListPopupImpl)popup).setCaption(XDebuggerBundle.message("xdebugger.attach.toRemote.popup.title", remoteName));
            }
          };
          popup.addListSelectionListener(listener);

          // force first valueChanged event
          listener.valueChanged(new ListSelectionEvent(mainList, mainList.getMinSelectionIndex(), mainList.getMaxSelectionIndex(), false));

          popup.showCenteredInCurrentWindow(project);
        });
      }
    }.queue();
  }

  @NotNull
  public static List<RemoteAttachItem> collectRemotes(@NotNull final Project project,
                                                      @NotNull XRemoteProcessListProvider[] remotes,
                                                      @NotNull XRemoteAttachDebuggerProvider[] providers) {
    MultiMap<XAttachGroup, Pair<RemoteSdkCredentials, XRemoteProcessListProvider>> groupWithItems = new MultiMap<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (XRemoteProcessListProvider eachRemote : remotes) {
      List<RemoteSdkCredentials> credentials = eachRemote.getCredentialsList();

      for (RemoteSdkCredentials credential : credentials) {
        groupWithItems.putValue(eachRemote.getAttachGroup(), Pair.create(credential, eachRemote));
      }
    }

    ArrayList<XAttachGroup> sortedGroups = new ArrayList<>(groupWithItems.keySet());
    sortedGroups.sort(Comparator.comparingInt(XAttachGroup::getOrder));

    List<RemoteAttachItem> currentItems = new ArrayList<>();
    for (final XAttachGroup eachGroup : sortedGroups) {
      List<Pair<RemoteSdkCredentials, XRemoteProcessListProvider>> sortedItems = new ArrayList<>(groupWithItems.get(eachGroup));
      sortedItems.sort((a, b) -> eachGroup.compare(project, a.first, b.first, dataHolder));

      boolean isFirst = true;
      for (Pair<RemoteSdkCredentials, XRemoteProcessListProvider> eachItem : sortedItems) {
        currentItems.add(new RemoteAttachItem(eachGroup, isFirst, providers, eachItem.second, eachItem.first, dataHolder, project));
        isFirst = false;
      }
    }

    return currentItems;
  }

  private static MultiMap<XAttachGroup, Pair<ProcessInfo, ArrayList<XAttachDebugger>>> getGroupsWithItems(@NotNull final Project project,
                                                                                                          @NotNull ProcessInfo[] processList,
                                                                                                          @NotNull ProgressIndicator indicator,
                                                                                                          @NotNull XAttachDebuggerProvider... providers) {
    MultiMap<XAttachGroup, Pair<ProcessInfo, ArrayList<XAttachDebugger>>> groupWithItems = new MultiMap<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();
    for (ProcessInfo eachInfo : processList) {

      MultiMap<XAttachGroup, XAttachDebugger> groupsWithDebuggers = new MultiMap<>();
      for (XAttachDebuggerProvider eachProvider : providers) {
        indicator.checkCanceled();
        groupsWithDebuggers.putValues(eachProvider.getAttachGroup(), eachProvider.getAvailableDebuggers(project, eachInfo, dataHolder));
      }

      for (XAttachGroup eachGroup : groupsWithDebuggers.keySet()) {
        Collection<XAttachDebugger> debuggers = groupsWithDebuggers.get(eachGroup);
        if (!debuggers.isEmpty()) {
          groupWithItems.putValue(eachGroup, Pair.create(eachInfo, new ArrayList<>(debuggers)));
        }
      }
    }

    return groupWithItems;
  }

  @NotNull
  public static List<AttachToLocalProcessItem> collectLocalAttachItems(@NotNull final Project project,
                                                                       @NotNull ProcessInfo[] processList,
                                                                       @NotNull ProgressIndicator indicator,
                                                                       @NotNull XAttachDebuggerProvider... providers) {
    MultiMap<XAttachGroup, Pair<ProcessInfo, ArrayList<XAttachDebugger>>> groupWithItems =
      getGroupsWithItems(project, processList, indicator, providers);

    ArrayList<XAttachGroup> sortedGroups = getAndSortAttachGroups(project, processList, indicator, providers);

    List<AttachToLocalProcessItem> currentItems = new ArrayList<>();
    for (final XAttachGroup eachGroup : sortedGroups) {
      List<Pair<ProcessInfo, ArrayList<XAttachDebugger>>> sortedItems
        = new ArrayList<>(groupWithItems.get(eachGroup));
      sortedItems.sort((a, b) -> eachGroup.compare(project, a.first, b.first, dataHolder));

      boolean first = true;
      for (Pair<ProcessInfo, ArrayList<XAttachDebugger>> eachItem : sortedItems) {
        currentItems.add(new AttachToLocalProcessItem(eachGroup, first, eachItem.first, eachItem.second, dataHolder));
        first = false;
      }
    }

    List<AttachToLocalProcessItem> currentHistoryItems = new ArrayList<>();
    List<HistoryItem> history = getHistory(project);
    for (int i = history.size() - 1; i >= 0; i--) {
      HistoryItem eachHistoryItem = history.get(i);
      for (AttachToLocalProcessItem eachCurrentItem : currentItems) {
        boolean isSuitableItem = eachHistoryItem.getGroup().equals(eachCurrentItem.getGroup()) &&
                                 eachHistoryItem.getProcessInfo().getCommandLine()
                                   .equals(eachCurrentItem.getProcessInfo().getCommandLine());
        if (!isSuitableItem) continue;

        List<XAttachDebugger> debuggers = eachCurrentItem.getDebuggers();
        int selectedDebugger = -1;
        for (int j = 0; j < debuggers.size(); j++) {
          XAttachDebugger eachDebugger = debuggers.get(j);
          if (eachDebugger.getDebuggerDisplayName().equals(eachHistoryItem.getDebuggerName())) {
            selectedDebugger = j;
            break;
          }
        }
        if (selectedDebugger == -1) continue;

        currentHistoryItems.add(new AttachToLocalProcessItem(eachCurrentItem.getGroup(),
                                                             currentHistoryItems.isEmpty(),
                                                             XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent"),
                                                             eachCurrentItem.getProcessInfo(),
                                                             debuggers,
                                                             selectedDebugger,
                                                             dataHolder));
      }
    }

    currentHistoryItems.addAll(currentItems);
    return currentHistoryItems;
  }

  public static void addToHistory(@NotNull Project project, @NotNull AttachToLocalProcessItem item) {

    LinkedHashMap<String, HistoryItem> history = project.getUserData(HISTORY_KEY);
    if (history == null) {
      project.putUserData(HISTORY_KEY, history = new LinkedHashMap<>());
    }
    ProcessInfo processInfo = item.getProcessInfo();
    history.remove(processInfo.getCommandLine());
    history.put(processInfo.getCommandLine(), new HistoryItem(processInfo, item.getGroup(),
                                                              item.getSelectedDebugger().getDebuggerDisplayName()));
    while (history.size() > 4) {
      history.remove(history.keySet().iterator().next());
    }
  }

  @NotNull
  public static List<HistoryItem> getHistory(@NotNull Project project) {
    LinkedHashMap<String, HistoryItem> history = project.getUserData(HISTORY_KEY);
    return history == null ? Collections.emptyList()
                           : Collections.unmodifiableList(new ArrayList<>(history.values()));
  }

  public static class HistoryItem {
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final XAttachGroup myGroup;
    @NotNull private final String myDebuggerName;

    public HistoryItem(@NotNull ProcessInfo processInfo,
                       @NotNull XAttachGroup group,
                       @NotNull String debuggerName) {
      myProcessInfo = processInfo;
      myGroup = group;
      myDebuggerName = debuggerName;
    }

    @NotNull
    public ProcessInfo getProcessInfo() {
      return myProcessInfo;
    }

    @NotNull
    public XAttachGroup getGroup() {
      return myGroup;
    }

    @NotNull
    public String getDebuggerName() {
      return myDebuggerName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AttachToProcessAction.HistoryItem item = (AttachToProcessAction.HistoryItem)o;

      if (!myProcessInfo.equals(item.myProcessInfo)) return false;
      if (!myGroup.equals(item.myGroup)) return false;
      if (!myDebuggerName.equals(item.myDebuggerName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myProcessInfo.hashCode();
      result = 31 * result + myGroup.hashCode();
      result = 31 * result + myDebuggerName.hashCode();
      return result;
    }
  }

  public interface AttachItem<T> {
    @Nullable
    String getSeparatorTitle();

    @Nullable
    Icon getIcon(@NotNull Project project);

    @NotNull
    String getText(@NotNull Project project);

    @NotNull
    List<T> getSubItems();
  }

  public static class RemoteAttachItem implements AttachItem<RemoteAttachItem> {
    @NotNull private final Project myProject;
    @NotNull private final XAttachGroup<RemoteSdkCredentials> myGroup;
    private final boolean myIsFirstInGroup;
    @NotNull private final String myGroupName;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private final XRemoteProcessListProvider myProcessListProvider;
    @NotNull private final RemoteSdkCredentials myCredentials;
    @NotNull private final XRemoteAttachDebuggerProvider[] myRemoteProviders;
    @NotNull private final List<RemoteAttachItem> mySubItems;

    public RemoteAttachItem(@NotNull XAttachGroup group,
                            boolean isFirstInGroup,
                            @NotNull XRemoteAttachDebuggerProvider[] remoteProviders,
                            @NotNull XRemoteProcessListProvider processListProvider,
                            @NotNull RemoteSdkCredentials credentials,
                            @NotNull UserDataHolder dataHolder,
                            @NotNull Project project) {
      this(group, isFirstInGroup, group.getGroupName(), remoteProviders, processListProvider, credentials, dataHolder, project);
    }

    public RemoteAttachItem(@NotNull XAttachGroup group,
                            boolean isFirstInGroup,
                            @NotNull String groupName,
                            @NotNull XRemoteAttachDebuggerProvider[] remoteProviders,
                            @NotNull XRemoteProcessListProvider processListProvider,
                            @NotNull RemoteSdkCredentials credentials,
                            @NotNull UserDataHolder dataHolder,
                            @NotNull Project project) {
      myGroupName = groupName;
      myDataHolder = dataHolder;
      myRemoteProviders = remoteProviders;
      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myCredentials = credentials;
      myProcessListProvider = processListProvider;
      mySubItems = Collections.emptyList();
      myProject = project;
    }

    @NotNull
    public List<RemoteAttachItem> getSubItems() {
      return mySubItems;
    }

    @Nullable
    public String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroupName : null;
    }

    @Nullable
    public Icon getIcon(@NotNull Project project) {
      return myGroup.getIcon(project, myCredentials, myDataHolder);
    }

    @NotNull
    public String getText(@NotNull Project project) {
      return StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, myCredentials, myDataHolder), 200, 0);
    }

    @NotNull
    public XAttachGroup getGroup() {
      return myGroup;
    }

    @NotNull
    public List<AttachToLocalProcessItem> getAttachItems() {

      return collectAttachItems(myProject, myProcessListProvider.getProcessList(), DumbProgressIndicator.INSTANCE, myRemoteProviders);
    }
  }

  private interface AttachSettings {
    String getText();
  }

  private static class RemoteAttachSettings implements AttachSettings {

  }

  private static class LocalAttachSettings implements AttachSettings {
    @NotNull private ProcessInfo myInfo;
    @NotNull private Project myProject;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private XAttachGroup<ProcessInfo> myGroup;

    @NotNull
    public String getText() {
      String shortenedText =
       StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(myProject, myInfo, myDataHolder), 200, 0);
      return myInfo.getPid() + " " + shortenedText;
    }
  }

  public static class AttachToProcessItem<T1, T2 extends AttachSettings> implements AttachItem<T1> {
    @NotNull private XAttachGroup<T2> myGroup;
    private boolean myIsFirstInGroup;
    @NotNull private String myGroupName;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private T2 myInfo;
    @NotNull private List<T2> myDebuggers;
    private int mySelectedDebugger;
    @NotNull private List<T1> mySubItems;

    @NotNull
    public T2 getProcessInfo() {
      return myInfo;
    }

    @NotNull
    public XAttachGroup getGroup() {
      return myGroup;
    }

    @Nullable
    public String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroupName : null;
    }

    @Nullable
    public Icon getIcon(@NotNull Project project) {
      return myGroup.getIcon(project, myInfo, myDataHolder);
    }

    @NotNull
    public String getText(@NotNull Project project) {
      //String shortenedText =
      //  StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, myInfo, myDataHolder), 200, 0);
      return myInfo.getText();
      //getPid() + " " + shortenedText;
    }

    @NotNull
    public List<T2> getDebuggers() {
      return myDebuggers;
    }

    @NotNull
    public List<T1> getSubItems() {
      return mySubItems;
    }

    @NotNull
    public T2 getSelectedDebugger() {
      return myDebuggers.get(mySelectedDebugger);
    }
  }

  //public static class AttachToRemoteProcessItem implements AttachItem<AttachToRemoteProcessItem> {
  //
  //}

  public static class AttachToLocalProcessItem extends AttachToProcessItem<AttachToLocalProcessItem, XLocalAttachDebugger>
    implements AttachItem<AttachToLocalProcessItem> {
    @NotNull private final XAttachGroup myGroup;
    private final boolean myIsFirstInGroup;
    @NotNull private final String myGroupName;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final List<XLocalAttachDebugger> myDebuggers;
    private final int mySelectedDebugger;
    @NotNull private final List<AttachToLocalProcessItem> mySubItems;

    public AttachToLocalProcessItem(@NotNull XAttachGroup group,
                                    boolean isFirstInGroup,
                                    @NotNull ProcessInfo info,
                                    @NotNull List<XLocalAttachDebugger> debuggers,
                                    @NotNull UserDataHolder dataHolder) {
      this(group, isFirstInGroup, group.getGroupName(), info, debuggers, 0, dataHolder);
    }

    public AttachToLocalProcessItem(@NotNull XAttachGroup group,
                                    boolean isFirstInGroup,
                                    @NotNull String groupName,
                                    @NotNull ProcessInfo info,
                                    @NotNull List<XLocalAttachDebugger> debuggers,
                                    int selectedDebugger,
                                    @NotNull UserDataHolder dataHolder) {
      myGroupName = groupName;
      myDataHolder = dataHolder;
      assert !debuggers.isEmpty() : "debugger list should not be empty";
      assert selectedDebugger >= 0 && selectedDebugger < debuggers.size() : "wrong selected debugger index";

      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myProcessInfo = info;
      myDebuggers = debuggers;
      mySelectedDebugger = selectedDebugger;

      if (debuggers.size() > 1) {
        mySubItems = ContainerUtil
          .map(debuggers,
               debugger -> new AttachToLocalProcessItem(myGroup, false, myProcessInfo, Collections.singletonList(debugger), dataHolder));
      }
      else {
        mySubItems = Collections.emptyList();
      }
    }

    public void startDebugSession(@NotNull Project project) {
      XLocalAttachDebugger debugger = getSelectedDebugger();
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach.local"));
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach.local." + debugger.getDebuggerDisplayName()));

      try {
        debugger.attachDebugSession(project, myProcessInfo);
      }
      catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, myProcessInfo.getExecutableName(), e);
      }
    }
  }

  public static class AttachToRemoteProcessItem extends AttachToProcessItem<AttachToRemoteProcessItem, XRemoteAttachDebugger>
    implements AttachItem<AttachToRemoteProcessItem> {
    @NotNull private final XAttachGroup myGroup;
    private final boolean myIsFirstInGroup;
    @NotNull private final String myGroupName;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final List<XRemoteAttachDebugger> myDebuggers;
    private final int mySelectedDebugger;
    @NotNull private final List<AttachToLocalProcessItem> mySubItems;

    public AttachToRemoteProcessItem(@NotNull XAttachGroup group,
                                     boolean isFirstInGroup,
                                     @NotNull ProcessInfo info,
                                     @NotNull List<XRemoteAttachDebugger> debuggers,
                                     @NotNull UserDataHolder dataHolder) {
      this(group, isFirstInGroup, group.getGroupName(), info, debuggers, 0, dataHolder);
    }

    public AttachToRemoteProcessItem(@NotNull XAttachGroup group,
                                     boolean isFirstInGroup,
                                     @NotNull String groupName,
                                     @NotNull ProcessInfo info,
                                     @NotNull List<XRemoteAttachDebugger> debuggers,
                                     int selectedDebugger,
                                     @NotNull UserDataHolder dataHolder) {
      myGroupName = groupName;
      myDataHolder = dataHolder;
      assert !debuggers.isEmpty() : "debugger list should not be empty";
      assert selectedDebugger >= 0 && selectedDebugger < debuggers.size() : "wrong selected debugger index";

      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myProcessInfo = info;
      myDebuggers = debuggers;
      mySelectedDebugger = selectedDebugger;

      if (debuggers.size() > 1) {
        mySubItems = ContainerUtil
          .map(debuggers,
               debugger -> new AttachToLocalProcessItem(myGroup, false, myProcessInfo, Collections.singletonList(debugger), dataHolder));
      }
      else {
        mySubItems = Collections.emptyList();
      }
    }

    public void startDebugSession(@NotNull Project project) {
      XRemoteAttachDebugger debugger = getSelectedDebugger();
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach.remote"));
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach.remote." + debugger.getDebuggerDisplayName()));

      try {
        ((XLocalAttachDebugger)debugger).attachDebugSession(project, myProcessInfo);
      }
      catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, myProcessInfo.getExecutableName(), e);
      }
    }
  }

  private static class MyBasePopupStep extends BaseListPopupStep<AttachItem> {
    @NotNull final Project myProject;

    public MyBasePopupStep(@NotNull Project project,
                           @Nullable String title,
                           List<? extends AttachItem> values) {
      super(title, values);
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

    @Override
    public boolean hasSubstep(AttachItem selectedValue) {
      return !selectedValue.getSubItems().isEmpty();
    }

    @NotNull
    MyBasePopupStep getSubStep(@NotNull RemoteAttachItem item) {

      return new MyBasePopupStep(myProject, "tmpTitle", item.getAttachItems());
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
      if (selectedValue instanceof AttachToProcessItem) {
        addToHistory(myProject, (AttachToLocalProcessItem)selectedValue);
        return doFinalStep(() -> ((AttachToLocalProcessItem)selectedValue).startDebugSession(myProject));
      }

      if (selectedValue instanceof RemoteAttachItem) {
        return getSubStep((RemoteAttachItem)selectedValue);
      }

      return null;
    }
  }

  private static class AttachListStep extends MyBasePopupStep implements ListPopupStepEx<AttachItem> {
    public AttachListStep(@NotNull List<AttachItem> items, @NotNull Project project) {
      super(project, XDebuggerBundle.message("xdebugger.attach.popup.title.default"), items);
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

    @Nullable
    @Override
    public String getTooltipTextFor(AttachItem value) {
      return value.getText(myProject);
    }

    @Override
    public void setEmptyText(@NotNull StatusText emptyText) {
      emptyText.setText(XDebuggerBundle.message("xdebugger.attach.toLocal.popup.emptyText"));
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
      if (finalChoice) {
        return super.onChosen(selectedValue, true);
      }
      if (selectedValue instanceof AttachToLocalProcessItem) {
        return new DebuggerListStep(selectedValue.getSubItems(), ((AttachToLocalProcessItem)selectedValue).mySelectedDebugger);
      }

      return null;
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue,
                              boolean finalChoice,
                              @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers) {
      return onChosen(selectedValue, finalChoice);
    }

    private class DebuggerListStep extends MyBasePopupStep {
      public DebuggerListStep(List<AttachItem> items, int selectedItem) {
        super(AttachListStep.this.myProject,
              XDebuggerBundle.message("xdebugger.attach.toLocal.popup.selectDebugger.title"), items);
        setDefaultOptionIndex(selectedItem);
      }

      @NotNull
      @Override
      public String getTextFor(AttachItem value) {
        if (value instanceof AttachToLocalProcessItem) {
          return ((AttachToLocalProcessItem)value).getSelectedDebugger().getDebuggerDisplayName();
        }
        return "";
      }
    }
  }
}
