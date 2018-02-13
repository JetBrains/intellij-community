/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.EmptyProgressIndicator;
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
import com.intellij.ui.popup.async.AsyncPopupStep;
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
    int attachDebuggerProvidersNumber = Extensions.getExtensions(XAttachDebuggerProvider.EP).length;
    boolean enabled = project != null && attachDebuggerProvidersNumber > 0;
    e.getPresentation().setEnabledAndVisible(enabled);
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;


    new Task.Backgroundable(project,
                            XDebuggerBundle.message("xdebugger.attach.action.collectingItems"), true,
                            PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {

        List<AttachItem> allItems = getTopLevelItems(indicator, project);

        ApplicationManager.getApplication().invokeLater(() -> {
          AttachListStep step = new AttachListStep(allItems, XDebuggerBundle.message("xdebugger.attach.popup.title.default"), project);

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

            if (item instanceof AttachHostItem) {
              AttachHostItem hostItem = (AttachHostItem)item;
              String attachHostName = hostItem.myGroup.getItemDisplayText(project, hostItem.myInfo, hostItem.myDataHolder);
              attachHostName = StringUtil.shortenTextWithEllipsis(attachHostName, 50, 0);

              ((ListPopupImpl)popup).setCaption(XDebuggerBundle.message("xdebugger.attach.host.popup.title", attachHostName));
            }
          };
          popup.addListSelectionListener(listener);

          // force first valueChanged event
          listener.valueChanged(new ListSelectionEvent(mainList, mainList.getMinSelectionIndex(), mainList.getMaxSelectionIndex(), false));

          popup.showCenteredInCurrentWindow(project);
        }, project.getDisposed());
      }
    }.queue();
  }

  @NotNull
  private static List<AttachItem> getTopLevelItems(@NotNull ProgressIndicator indicator, @NotNull Project project) {
    XAttachDebuggerProvider[] attachDebuggerProviders = XAttachDebuggerProvider.getAttachDebuggerProviders();

    XAttachHostProvider[] attachHostProviders = Extensions.getExtensions(XAttachHostProvider.EP);

    List<ProcessInfo> localAttachInfos = LocalAttachHost.INSTANCE.getProcessList(project);

    List<AttachToProcessItem> localAttachToProcessItems = collectAttachItems(
      project, LocalAttachHost.INSTANCE, localAttachInfos, indicator, attachDebuggerProviders
    );
    List<AttachItem> remoteAttachToProcessItems = collectRemotes(
      project, attachHostProviders, attachDebuggerProviders
    );

    return ContainerUtil.concat(remoteAttachToProcessItems, localAttachToProcessItems);
  }

  @NotNull
  public static List<AttachItem> collectRemotes(@NotNull final Project project,
                                                @NotNull XAttachHostProvider[] hostProviders,
                                                @NotNull XAttachDebuggerProvider... attachDebuggerProviders) {
    MultiMap<XAttachPresentationGroup<XAttachHost>, Pair<XAttachHost, XAttachHostProvider>> groupWithItems = new MultiMap<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (XAttachHostProvider hostProvider : hostProviders) {
      //noinspection unchecked
      List<XAttachHost> settingsList = hostProvider.getAvailableHosts();

      for (XAttachHost eachSettings : settingsList) {
        groupWithItems.putValue(hostProvider.getPresentationGroup(), Pair.create(eachSettings, hostProvider));
      }
    }

    ArrayList<XAttachPresentationGroup<XAttachHost>> sortedGroups = new ArrayList<>(groupWithItems.keySet());
    sortedGroups.sort(Comparator.comparingInt(XAttachPresentationGroup::getOrder));

    List<AttachItem> currentItems = new ArrayList<>();
    for (final XAttachPresentationGroup<XAttachHost> eachGroup : sortedGroups) {

      Set<Pair<XAttachHost, XAttachHostProvider>> sortedItems =
        new TreeSet<>((o1, o2) -> eachGroup.compare(project, o1.first, o2.first, dataHolder));
      sortedItems.addAll(groupWithItems.get(eachGroup));

      boolean isFirst = true;
      for (Pair<XAttachHost, XAttachHostProvider> eachItem : sortedItems) {
        currentItems
          .add(new AttachHostItem(eachGroup, isFirst, eachGroup.getGroupName(), eachItem.second, eachItem.first, project, dataHolder,
                                  attachDebuggerProviders));
        isFirst = false;
      }
    }

    return currentItems;
  }

  private static void addHistoryItems(@NotNull List<HistoryItem> history,
                                      @NotNull List<AttachToProcessItem> currentItems,
                                      @NotNull Project project,
                                      @NotNull UserDataHolder dataHolder,
                                      @NotNull List<AttachToProcessItem> result) {
    for (int i = history.size() - 1; i >= 0; i--) {
      HistoryItem eachHistoryItem = history.get(i);
      for (AttachToProcessItem eachCurrentItem : currentItems) {
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

        result.add(new AttachToProcessItem(eachCurrentItem.getGroup(),
                                           result.isEmpty(),
                                           XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent"),
                                           eachCurrentItem.getHost(),
                                           eachCurrentItem.getProcessInfo(),
                                           debuggers,
                                           selectedDebugger,
                                           project,
                                           dataHolder));
      }
    }
  }

  @NotNull
  static List<AttachToProcessItem> collectAttachItems(@NotNull final Project project,
                                                      @NotNull XAttachHost hostInfo,
                                                      @NotNull List<ProcessInfo> processInfos,
                                                      @NotNull ProgressIndicator indicator,
                                                      @NotNull XAttachDebuggerProvider... providers) {
    MultiMap<XAttachPresentationGroup<ProcessInfo>, Pair<ProcessInfo, ArrayList<XAttachDebugger>>> groupWithItems = new MultiMap<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (ProcessInfo process : processInfos) {

      MultiMap<XAttachPresentationGroup<ProcessInfo>, XAttachDebugger> groupsWithDebuggers = new MultiMap<>();

      for (XAttachDebuggerProvider eachProvider : providers) {
        indicator.checkCanceled();
        if(eachProvider.isAttachHostApplicable(hostInfo)) {
          groupsWithDebuggers.putValues(eachProvider.getPresentationGroup(),
                                        eachProvider.getAvailableDebuggers(project, hostInfo, process, dataHolder));
        }
      }

      for (XAttachPresentationGroup<ProcessInfo> eachGroup : groupsWithDebuggers.keySet()) {
        Collection<XAttachDebugger> debuggers = groupsWithDebuggers.get(eachGroup);
        if (!debuggers.isEmpty()) {
          groupWithItems.putValue(eachGroup, Pair.create(process, new ArrayList<>(debuggers)));
        }
      }
    }

    ArrayList<XAttachPresentationGroup<ProcessInfo>> sortedGroups = new ArrayList<>(groupWithItems.keySet());
    sortedGroups.sort(Comparator.comparingInt(XAttachPresentationGroup::getOrder));

    List<AttachToProcessItem> currentItems = new ArrayList<>();
    for (final XAttachPresentationGroup<ProcessInfo> eachGroup : sortedGroups) {
      List<Pair<ProcessInfo, ArrayList<XAttachDebugger>>> sortedItems = new ArrayList<>(groupWithItems.get(eachGroup));
      sortedItems.sort((a, b) -> eachGroup.compare(project,
                                                   a.first,
                                                   b.first,
                                                   dataHolder));

      boolean first = true;
      for (Pair<ProcessInfo, ArrayList<XAttachDebugger>> eachItem : sortedItems) {
        currentItems.add(new AttachToProcessItem(eachGroup, first, hostInfo, eachItem.first, eachItem.second, project, dataHolder));
        first = false;
      }
    }

    List<AttachToProcessItem> result = new ArrayList<>();

    addHistoryItems(getHistory(project), currentItems, project, dataHolder, result);

    result.addAll(currentItems);
    return result;
  }

  public static void addToHistory(@NotNull Project project, @NotNull AttachToProcessItem item) {
    LinkedHashMap<String, HistoryItem> history = project.getUserData(HISTORY_KEY);
    if (history == null) {
      project.putUserData(HISTORY_KEY, history = new LinkedHashMap<>());
    }
    ProcessInfo attachInfo = item.getProcessInfo();
    String commandLine = attachInfo.getCommandLine();
    history.remove(commandLine);
    history.put(commandLine, new HistoryItem(item.getHost(), attachInfo, item.getGroup(),
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
    @NotNull private final XAttachHost myHost;
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final XAttachPresentationGroup myGroup;
    @NotNull private final String myDebuggerName;

    public HistoryItem(@NotNull XAttachHost host,
                       @NotNull ProcessInfo item,
                       @NotNull XAttachPresentationGroup group,
                       @NotNull String debuggerName) {
      myHost = host;
      myProcessInfo = item;
      myGroup = group;
      myDebuggerName = debuggerName;
    }

    @NotNull
    public XAttachHost getHost() {
      return myHost;
    }

    @NotNull
    public ProcessInfo getProcessInfo() {
      return myProcessInfo;
    }

    @NotNull
    public XAttachPresentationGroup getGroup() {
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
      HistoryItem item = (HistoryItem)o;
      return Objects.equals(myHost, item.myHost) &&
             Objects.equals(myProcessInfo, item.myProcessInfo) &&
             Objects.equals(myGroup, item.myGroup) &&
             Objects.equals(myDebuggerName, item.myDebuggerName);
    }

    @Override
    public int hashCode() {

      return Objects.hash(myHost, myProcessInfo, myGroup, myDebuggerName);
    }
  }

  public static abstract class AttachItem<T> {

    XAttachPresentationGroup<T> myGroup;
    boolean myIsFirstInGroup;
    String myGroupName;
    Project myProject;
    UserDataHolder myDataHolder;
    T myInfo;

    public AttachItem(@NotNull XAttachPresentationGroup<T> group,
                      boolean isFirstInGroup,
                      @NotNull String groupName,
                      @NotNull T info,
                      @NotNull Project project,
                      @NotNull UserDataHolder dataHolder) {
      myGroup = group;
      myIsFirstInGroup = isFirstInGroup;
      myInfo = info;
      myProject = project;
      myDataHolder = dataHolder;
      myGroupName = groupName;
    }

    @NotNull
    XAttachPresentationGroup<T> getGroup() {
      return myGroup;
    }

    @Nullable
    String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroupName : null;
    }

    @Nullable
    Icon getIcon(@NotNull Project project) {
      return myGroup.getIcon(project, myInfo, myDataHolder);
    }

    abstract boolean hasSubStep();

    abstract String getText(@NotNull Project project);

    abstract List<AttachToProcessItem> getSubItems();
  }

  private static class AttachHostItem extends AttachItem<XAttachHost> {

    @NotNull XAttachHostProvider myProvider;
    @NotNull XAttachDebuggerProvider[] myAttachProviders;

    public AttachHostItem(@NotNull XAttachPresentationGroup<XAttachHost> group,
                          boolean isFirstInGroup,
                          @NotNull String groupName,
                          @NotNull XAttachHostProvider provider,
                          @NotNull XAttachHost info,
                          @NotNull Project project,
                          @NotNull UserDataHolder dataHolder,
                          @NotNull XAttachDebuggerProvider[] attachProviders) {
      super(group, isFirstInGroup, groupName, info, project, dataHolder);
      myProvider = provider;
      myAttachProviders = attachProviders;
    }

    public boolean hasSubStep() {
      return true;
    }

    @NotNull
    @Override
    public String getText(@NotNull Project project) {
      return myGroup.getItemDisplayText(project, myInfo, myDataHolder);
    }

    @Nullable
    public String getTooltipText(@NotNull Project project)  {
      return myGroup.getItemDescription(project, myInfo, myDataHolder);
    }

    @Override
    public List<AttachToProcessItem> getSubItems() {
      List<ProcessInfo> processInfos = myInfo.getProcessList(myProject);

      return collectAttachItems(myProject, myInfo, processInfos, new EmptyProgressIndicator(), myAttachProviders);
    }
  }

  public static class AttachToProcessItem extends AttachItem<ProcessInfo> {
    @NotNull private final List<XAttachDebugger> myDebuggers;
    private final int mySelectedDebugger;
    @NotNull private final List<AttachToProcessItem> mySubItems;
    @NotNull private final XAttachHost myHost;

    public AttachToProcessItem(@NotNull XAttachPresentationGroup<ProcessInfo> group,
                               boolean isFirstInGroup,
                               @NotNull XAttachHost host,
                               @NotNull ProcessInfo info,
                               @NotNull List<XAttachDebugger> debuggers,
                               @NotNull Project project,
                               @NotNull UserDataHolder dataHolder) {
      this(group, isFirstInGroup, group.getGroupName(), host, info, debuggers, 0, project, dataHolder);
    }

    public AttachToProcessItem(@NotNull XAttachPresentationGroup<ProcessInfo> group,
                               boolean isFirstInGroup,
                               @NotNull String groupName,
                               @NotNull XAttachHost host,
                               @NotNull ProcessInfo info,
                               @NotNull List<XAttachDebugger> debuggers,
                               int selectedDebugger,
                               @NotNull Project project,
                               @NotNull UserDataHolder dataHolder) {
      super(group, isFirstInGroup, groupName, info, project, dataHolder);
      assert !debuggers.isEmpty() : "debugger list should not be empty";
      assert selectedDebugger >= 0 && selectedDebugger < debuggers.size() : "wrong selected debugger index";

      myDebuggers = debuggers;
      mySelectedDebugger = selectedDebugger;
      myHost = host;

      if (debuggers.size() > 1) {
        mySubItems = ContainerUtil
          .map(debuggers,
               debugger -> new AttachToProcessItem(myGroup, false, myHost, myInfo, Collections.singletonList(debugger), myProject, dataHolder));
      }
      else {
        mySubItems = Collections.emptyList();
      }
    }

    @NotNull
    public ProcessInfo getProcessInfo() {
      return myInfo;
    }

    @NotNull
    public XAttachHost getHost() {
      return myHost;
    }

    public boolean hasSubStep() {
      return !mySubItems.isEmpty();
    }

    @Nullable
    public String getTooltipText(@NotNull Project project)  {
      return myGroup.getItemDescription(project, myInfo, myDataHolder);
    }

    @NotNull
    public String getText(@NotNull Project project) {
      String shortenedText = StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, myInfo, myDataHolder), 200, 0);
      return myInfo.getPid() + " " + shortenedText;
    }

    @NotNull
    public List<XAttachDebugger> getDebuggers() {
      return myDebuggers;
    }

    @NotNull
    public List<AttachToProcessItem> getSubItems() {
      return mySubItems;
    }

    @NotNull
    public XAttachDebugger getSelectedDebugger() {
      return myDebuggers.get(mySelectedDebugger);
    }

    public void startDebugSession(@NotNull Project project) {
      XAttachDebugger debugger = getSelectedDebugger();
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach"));
      UsageTrigger.trigger(ConvertUsagesUtil.ensureProperKey("debugger.attach." + debugger.getDebuggerDisplayName()));

      try {
        debugger.attachDebugSession(project, myHost, myInfo);
      }
      catch (ExecutionException e) {
        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, "pid " + myInfo.getPid(), e);
      }
    }
  }

  private static class MyBasePopupStep<T extends AttachItem> extends BaseListPopupStep<T> {
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
    public boolean hasSubstep(AttachItem selectedValue) {
      return !selectedValue.getSubItems().isEmpty();
    }

    @Override
    public PopupStep onChosen(T selectedValue, boolean finalChoice) {
      return null;
    }
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

    @Override
    public boolean hasSubstep(AttachItem selectedValue) {
      return selectedValue.hasSubStep();
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
        AttachToProcessItem attachToProcessItem = (AttachToProcessItem)selectedValue;
        if (finalChoice) {
          addToHistory(myProject, attachToProcessItem);
          return doFinalStep(() -> attachToProcessItem.startDebugSession(myProject));
        }
        else {
          return new DebuggerListStep(attachToProcessItem.getSubItems(), attachToProcessItem.mySelectedDebugger);
        }
      }

      if (selectedValue instanceof AttachHostItem) {
        AttachHostItem attachHostItem = (AttachHostItem)selectedValue;
        return new AsyncPopupStep() {
          @Override
          public PopupStep call() throws Exception {
            List<AttachItem> attachItems = new ArrayList<>(attachHostItem.getSubItems());
            return new AttachListStep(attachItems, null, myProject);
          }
        };
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
        super(AttachListStep.this.myProject, XDebuggerBundle.message("xdebugger.attach.popup.selectDebugger.title"), items);
        setDefaultOptionIndex(selectedItem);
      }

      @NotNull
      @Override
      public String getTextFor(AttachToProcessItem value) {
        return value.getSelectedDebugger().getDebuggerDisplayName();
      }

      @Override
      public PopupStep onChosen(AttachToProcessItem selectedValue, boolean finalChoice) {
        addToHistory(myProject, selectedValue);
        return doFinalStep(() -> selectedValue.startDebugSession(myProject));
      }
    }
  }
}
