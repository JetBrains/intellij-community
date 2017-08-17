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
        List<AttachToProcessSettings> localSettings = new ArrayList<>();

        for (ProcessInfo info : processList) {
          localSettings.add(new LocalAttachSettings(info));
        }

        List<AttachToProcessItem> localAttachItems =
          collectAttachItems(project, localSettings, localAttachProviders); //indicator
        List<RemoteAttachItem> remoteAttachItems = collectRemotes(project, remotes, remoteAttachProviders);

        List<AttachItem> attachItems = new ArrayList<>(remoteAttachItems);
        attachItems.addAll(localAttachItems);

        ApplicationManager.getApplication().invokeLater(() -> {
          if (project.isDisposed()) {
            return;
          }
          AttachListStep step = new AttachListStep(attachItems, XDebuggerBundle.message("xdebugger.attach.popup.title.default"), project);

          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
          final JList mainList = ((ListPopupImpl)popup).getList();

          ListSelectionListener listener = event -> {
            if (event.getValueIsAdjusting()) return;

            Object item = ((JList)event.getSource()).getSelectedValue();

            // if a sub-list is closed, fallback to the selected value from the main list
            if (item == null) {
              item = mainList.getSelectedValue();
            }

            if (item instanceof AttachToProcessItem) {
              String debuggerName = ((AttachToProcessItem)item).getSelectedDebugger().getDebuggerDisplayName();
              debuggerName = StringUtil.shortenTextWithEllipsis(debuggerName, 50, 0);

              ((ListPopupImpl)popup).setCaption(XDebuggerBundle.message("xdebugger.attach.popup.title", debuggerName));
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

  //TODO rework
  @NotNull
  public static List<RemoteAttachItem> collectRemotes(@NotNull final Project project,
                                                      @NotNull XRemoteProcessListProvider[] remotes,
                                                      @NotNull XRemoteAttachDebuggerProvider[] providers) {
    MultiMap<XAttachGroup<RemoteSettings>, Pair<RemoteSettings, XRemoteProcessListProvider>> groupWithItems = new MultiMap<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (XRemoteProcessListProvider eachRemote : remotes) {
      List<RemoteSettings> settingsList = eachRemote.getSettingsList();

      for (RemoteSettings eachSettings : settingsList) {
        groupWithItems.putValue(eachRemote.getAttachGroup(), Pair.create(eachSettings, eachRemote));
      }
    }

    ArrayList<XAttachGroup<RemoteSettings>> sortedGroups = new ArrayList<>(groupWithItems.keySet());
    sortedGroups.sort(Comparator.comparingInt(XAttachGroup::getOrder));

    List<RemoteAttachItem> currentItems = new ArrayList<>();
    for (final XAttachGroup<RemoteSettings> eachGroup : sortedGroups) {

      List<Pair<RemoteSettings, XRemoteProcessListProvider>> sortedItems = new ArrayList<>(groupWithItems.get(eachGroup));
      sortedItems.sort((a, b) -> eachGroup.compare(project, a.first, b.first, dataHolder));

      boolean isFirst = true;
      for (Pair<RemoteSettings, XRemoteProcessListProvider> eachItem : sortedItems) {
        currentItems.add(new RemoteAttachItem(eachGroup, isFirst, providers, eachItem.second, eachItem.first, dataHolder, project));
        isFirst = false;
      }
    }

    return currentItems;
  }

  private static MultiMap<XAttachGroup<AttachToProcessSettings>, Pair<AttachToProcessSettings, ArrayList<XAttachDebugger<AttachToProcessSettings>>>> getGroupsWithItems(
    @NotNull final Project project,
    @NotNull List<AttachToProcessSettings> settingsList,
    //@NotNull ProgressIndicator indicator,
    @NotNull XAttachDebuggerProvider<AttachToProcessSettings>... providers) {
    MultiMap<XAttachGroup<AttachToProcessSettings>, Pair<AttachToProcessSettings, ArrayList<XAttachDebugger<AttachToProcessSettings>>>>
      groupWithItems = new MultiMap<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();
    for (AttachToProcessSettings eachInfo : settingsList) {

      MultiMap<XAttachGroup<AttachToProcessSettings>, XAttachDebugger<AttachToProcessSettings>> groupsWithDebuggers = new MultiMap<>();
      for (XAttachDebuggerProvider<AttachToProcessSettings> eachProvider : providers) {
        //indicator.checkCanceled();
        groupsWithDebuggers.putValues(eachProvider.getAttachGroup(), eachProvider.getAvailableDebuggers(project, eachInfo, dataHolder));
      }

      for (XAttachGroup<AttachToProcessSettings> eachGroup : groupsWithDebuggers.keySet()) {
        Collection<XAttachDebugger<AttachToProcessSettings>> debuggers = groupsWithDebuggers.get(eachGroup);
        if (!debuggers.isEmpty()) {
          groupWithItems.putValue(eachGroup, Pair.create(eachInfo, new ArrayList<>(debuggers)));
        }
      }
    }

    return groupWithItems;
  }

  @NotNull
  public static List<AttachToProcessItem> collectAttachItems(@NotNull final Project project,
                                                             @NotNull List<AttachToProcessSettings> settingsList,
                                                             //@NotNull ProgressIndicator indicator,
                                                             @NotNull XAttachDebuggerProvider... providers) {
    UserDataHolderBase dataHolder = new UserDataHolderBase();

    MultiMap<XAttachGroup<AttachToProcessSettings>, Pair<AttachToProcessSettings, ArrayList<XAttachDebugger<AttachToProcessSettings>>>>
      groupWithItems = getGroupsWithItems(project, settingsList, providers);
    //getGroupsWithItems(project, settingsList, indicator, providers);

    ArrayList<XAttachGroup<AttachToProcessSettings>> sortedGroups = new ArrayList<>(groupWithItems.keySet());
    sortedGroups.sort(Comparator.comparingInt(XAttachGroup::getOrder));

    List<AttachToProcessItem<AttachToProcessSettings>> currentItems = new ArrayList<>();
    for (final XAttachGroup<AttachToProcessSettings> eachGroup : sortedGroups) {
      List<Pair<AttachToProcessSettings, ArrayList<XAttachDebugger<AttachToProcessSettings>>>> sortedItems
        = new ArrayList<>(groupWithItems.get(eachGroup));
      sortedItems.sort((a, b) -> eachGroup.compare(project, a.first, b.first, dataHolder));

      boolean first = true;
      for (Pair<AttachToProcessSettings, ArrayList<XAttachDebugger<AttachToProcessSettings>>> eachItem : sortedItems) {
        currentItems.add(new AttachToProcessItem<>(eachGroup, first, eachItem.first, eachItem.second, dataHolder));
        first = false;
      }
    }

    List<AttachToProcessItem> currentHistoryItems = new ArrayList<>();
    List<HistoryItem> history = getHistory(project);
    for (int i = history.size() - 1; i >= 0; i--) {
      HistoryItem eachHistoryItem = history.get(i);
      for (AttachToProcessItem<AttachToProcessSettings> eachCurrentItem : currentItems) {
        boolean isSuitableItem = eachHistoryItem.getGroup().equals(eachCurrentItem.getGroup()) &&
                                 eachHistoryItem.getProcessInfo().getCommandLine()
                                   .equals(eachCurrentItem.getInfo().getInfo().getCommandLine());
        if (!isSuitableItem) continue;

        List<XAttachDebugger<AttachToProcessSettings>> debuggers = eachCurrentItem.getDebuggers();
        int selectedDebugger = -1;
        for (int j = 0; j < debuggers.size(); j++) {
          XAttachDebugger eachDebugger = debuggers.get(j);
          if (eachDebugger.getDebuggerDisplayName().equals(eachHistoryItem.getDebuggerName())) {
            selectedDebugger = j;
            break;
          }
        }
        if (selectedDebugger == -1) continue;

        currentHistoryItems.add(new AttachToProcessItem<>(eachCurrentItem.getGroup(),
                                                          currentHistoryItems.isEmpty(),
                                                          XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent"),
                                                          eachCurrentItem.getInfo(),
                                                          debuggers,
                                                          selectedDebugger,
                                                          dataHolder));
      }
    }

    currentHistoryItems.addAll(currentItems);
    return currentHistoryItems;
  }

  public static void addToHistory(@NotNull Project project, @NotNull AttachToProcessItem item) {

    LinkedHashMap<String, HistoryItem> history = project.getUserData(HISTORY_KEY);
    if (history == null) {
      project.putUserData(HISTORY_KEY, history = new LinkedHashMap<>());
    }
    ProcessInfo processInfo = item.getInfo().getInfo();

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

  public interface AttachItem {
    @Nullable
    String getSeparatorTitle();

    @Nullable
    Icon getIcon(@NotNull Project project);

    @NotNull
    String getText(@NotNull Project project);

    @NotNull
    XAttachGroup getGroup();

    //boolean hasSubStep();

    @NotNull
    List<AttachItem> getSubItems();
  }

  public static class RemoteAttachItem implements AttachItem {
    @NotNull private final Project myProject;
    //TODO ???
    @NotNull private final XAttachGroup<RemoteSettings> myGroup;
    private final boolean myIsFirstInGroup;
    @NotNull private final String myGroupName;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private final XRemoteProcessListProvider myProcessListProvider;
    @NotNull private final RemoteSettings mySettings;
    @NotNull private final XRemoteAttachDebuggerProvider[] myProviders;

    public RemoteAttachItem(@NotNull XAttachGroup<RemoteSettings> group,
                            boolean isFirstInGroup,
                            @NotNull XRemoteAttachDebuggerProvider[] remoteProviders,
                            @NotNull XRemoteProcessListProvider processListProvider,
                            @NotNull RemoteSettings settings,
                            @NotNull UserDataHolder dataHolder,
                            @NotNull Project project) {
      this(group, isFirstInGroup, group.getGroupName(), remoteProviders, processListProvider, settings, dataHolder, project);
    }

    public RemoteAttachItem(@NotNull XAttachGroup<RemoteSettings> group,
                            boolean isFirstInGroup,
                            @NotNull String groupName,
                            @NotNull XRemoteAttachDebuggerProvider[] remoteProviders,
                            @NotNull XRemoteProcessListProvider processListProvider,
                            @NotNull RemoteSettings settings,
                            @NotNull UserDataHolder dataHolder,
                            @NotNull Project project) {
      myGroupName = groupName;
      myDataHolder = dataHolder;
      myProviders = remoteProviders;
      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myProcessListProvider = processListProvider;
      mySettings = settings;
      myProject = project;
    }

    @Nullable
    @Override
    public String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroupName : null;
    }

    @Nullable
    @Override
    public Icon getIcon(@NotNull Project project) {
      return myGroup.getIcon(project, mySettings, myDataHolder);
    }

    @NotNull
    @Override
    public String getText(@NotNull Project project) {
      return StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, mySettings, myDataHolder), 200, 0);
    }

    @NotNull
    @Override
    public XAttachGroup getGroup() {
      return myGroup;
    }

    @NotNull
    @Override
    public List<AttachItem> getSubItems() {
      return getAttachItems();
    }

    @NotNull
    public List<AttachItem> getAttachItems() {
      List<AttachToProcessSettings> settings = new ArrayList<>();
      List<ProcessInfo> processInfo = myProcessListProvider.getProcessList();

      for (ProcessInfo info : processInfo) {
        settings.add(new RemoteAttachSettings(info, mySettings.getInfo()));
      }

      return new ArrayList<>(collectAttachItems(myProject, settings, myProviders));
    }
  }

  public static class AttachToProcessItem<T extends AttachToProcessSettings> implements AttachItem {
    @NotNull private XAttachGroup<T> myGroup;
    private boolean myIsFirstInGroup;
    @NotNull private String myGroupName;
    @NotNull private UserDataHolder myDataHolder;
    @NotNull private T myInfo;
    @NotNull private List<XAttachDebugger<T>> myDebuggers;
    private int mySelectedDebugger;
    @NotNull private List<AttachItem> mySubItems;

    public AttachToProcessItem(@NotNull XAttachGroup<T> group,
                               boolean isFirstInGroup,
                               @NotNull T info,
                               @NotNull List<XAttachDebugger<T>> debuggers,
                               @NotNull UserDataHolder dataHolder) {
      this(group, isFirstInGroup, group.getGroupName(), info, debuggers, 0, dataHolder);
    }

    public AttachToProcessItem(@NotNull XAttachGroup<T> group,
                               boolean isFirstInGroup,
                               @NotNull String groupName,
                               @NotNull T info,
                               @NotNull List<XAttachDebugger<T>> debuggers,
                               int selectedDebugger,
                               @NotNull UserDataHolder dataHolder) {
      myGroupName = groupName;
      myDataHolder = dataHolder;
      assert !debuggers.isEmpty() : "debugger list should not be empty";
      assert selectedDebugger >= 0 && selectedDebugger < debuggers.size() : "wrong selected debugger index";

      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myInfo = info;
      myDebuggers = debuggers;
      mySelectedDebugger = selectedDebugger;

      if (debuggers.size() > 1) {
        mySubItems = ContainerUtil
          .map(debuggers,
               debugger -> new AttachToProcessItem<>(myGroup, false, myInfo, Collections.singletonList(debugger), dataHolder));
      }
      else {
        mySubItems = Collections.emptyList();
      }
    }

    @NotNull
    public T getInfo() {
      return myInfo;
    }

    public T getProcessInfo() {
      return myInfo;
    }

    @NotNull
    public XAttachGroup<T> getGroup() {
      return myGroup;
    }

    //public boolean hasSubStep() {
    //  return !mySubItems.isEmpty();
    //}

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
      return StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, myInfo, myDataHolder), 200, 0);
      //return myInfo.getText(shortenedText);
    }

    @NotNull
    public List<XAttachDebugger<T>> getDebuggers() {
      return myDebuggers;
    }

    @NotNull
    public List<AttachItem> getSubItems() {
      return mySubItems;
    }

    @NotNull
    public XAttachDebugger<T> getSelectedDebugger() {
      return myDebuggers.get(mySelectedDebugger);
    }

    public void startDebugSession(@NotNull Project project) {
      XAttachDebugger<T> debugger = getSelectedDebugger();
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach"));
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach." + debugger.getDebuggerDisplayName()));

      try {
        debugger.attachDebugSession(project, myInfo);
      }
      catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, myInfo.getText(), e);
      }
    }
  }

  //public static class AttachToLocalProcessItem extends AttachToProcessItem<LocalAttachSettings> {
  //  public AttachToLocalProcessItem(@NotNull XAttachGroup<LocalAttachSettings> group,
  //                                  boolean isFirstInGroup,
  //                                  @NotNull LocalAttachSettings info,
  //                                  @NotNull List<XAttachDebugger<LocalAttachSettings>> debuggers,
  //                                  @NotNull UserDataHolder dataHolder) {
  //    super(group, isFirstInGroup, group.getGroupName(), info, debuggers, 0, dataHolder);
  //  }
  //}
  //
  //public static class AttachToRemoteProcessItem extends AttachToProcessItem<RemoteAttachSettings> {
  //  public AttachToRemoteProcessItem(@NotNull XAttachGroup<RemoteAttachSettings> group,
  //                                   boolean isFirstInGroup,
  //                                   @NotNull RemoteAttachSettings info,
  //                                   @NotNull List<XAttachDebugger<RemoteAttachSettings>> debuggers,
  //                                   @NotNull UserDataHolder dataHolder) {
  //    super(group, isFirstInGroup, group.getGroupName(), info, debuggers, 0, dataHolder);
  //  }
  //}

  private abstract static class MyBasePopupStep<T extends AttachItem> extends BaseListPopupStep<T> {
    @NotNull final Project myProject;

    public MyBasePopupStep(@NotNull Project project,
                           @Nullable String title,
                           List<T> values) {
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
    public boolean hasSubstep(T selectedValue) {
      return !selectedValue.getSubItems().isEmpty();
    }

    @Override
    public abstract PopupStep onChosen(T selectedValue, boolean finalChoice);
  }

  private static class AttachListStep extends MyBasePopupStep<AttachItem> implements ListPopupStepEx<AttachItem> {
    public AttachListStep(@NotNull List<AttachItem> items, @Nullable String title, @NotNull Project project) {
      super(project, title, items);
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
      emptyText.setText(XDebuggerBundle.message("xdebugger.attach.popup.emptyText"));
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue, boolean finalChoice) {
      if (selectedValue instanceof AttachToProcessItem) {
        if (finalChoice) {
          addToHistory(myProject, (AttachToProcessItem)selectedValue);
          return doFinalStep(() -> ((AttachToProcessItem)selectedValue).startDebugSession(myProject));
        }
        else {
          return new DebuggerListStep(((AttachToProcessItem)selectedValue).getSubItems(),
                                      ((AttachToProcessItem)selectedValue).mySelectedDebugger);
        }
      }

      if (selectedValue instanceof RemoteAttachItem) {
        return new AttachListStep(selectedValue.getSubItems(), null, myProject);
      }

      return null;
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue,
                              boolean finalChoice,
                              @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers) {
      return onChosen(selectedValue, finalChoice);
    }

    private class DebuggerListStep extends MyBasePopupStep<AttachToProcessItem> {
      public DebuggerListStep(List<AttachToProcessItem> items, int selectedItem) {
        super(AttachListStep.this.myProject,
              XDebuggerBundle.message("xdebugger.attach.toLocal.popup.selectDebugger.title"), items);
        setDefaultOptionIndex(selectedItem);
      }

      @NotNull
      @Override
      public String getTextFor(AttachToProcessItem value) {
        return value.getSelectedDebugger().getDebuggerDisplayName();
      }

      @Override
      //TODO rework
      public PopupStep onChosen(AttachToProcessItem selectedValue, boolean finalChoice) {
        addToHistory(myProject, selectedValue);
        return doFinalStep(() -> selectedValue.startDebugSession(myProject));
      }
    }
  }
}
