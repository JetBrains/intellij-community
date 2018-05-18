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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.popup.async.AsyncPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.StatusText;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.stream.Collectors;

public class AttachToProcessAction extends AnAction {
  private static final Key<Map<XAttachHost, LinkedHashSet<RecentItem>>> RECENT_ITEMS_KEY = Key.create("AttachToProcessAction.RECENT_ITEMS_KEY");
  private static final Logger LOG = Logger.getInstance(AttachToProcessAction.class);

  public AttachToProcessAction() {
    super(XDebuggerBundle.message("xdebugger.attach.action"),
          XDebuggerBundle.message("xdebugger.attach.action.description"), AllIcons.Debugger.AttachToProcess);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);

    Project project = getEventProject(e);
    int attachDebuggerProvidersNumber = XAttachDebuggerProvider.getAttachDebuggerProviders().size();
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
              String attachHostName = hostItem.getText(project);
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
    List<AttachToProcessItem> localAttachToProcessItems = collectAttachProcessItems(
      project, LocalAttachHost.INSTANCE, indicator
    );
    List<AttachItem> remoteAttachToProcessItems = collectAttachHostsItems(
      project, indicator
    );

    return ContainerUtil.concat(remoteAttachToProcessItems, localAttachToProcessItems);
  }

  private static void doUpdateFirstInGroup(@NotNull List<? extends AttachItem> items) {
    if(items.isEmpty()) {
      return;
    }

    items.get(0).makeFirstInGroup();

    for(int i = 1; i < items.size(); i++) {
      if(items.get(i).getGroup() != items.get(i - 1).getGroup()) {
        items.get(i).makeFirstInGroup();
      }
    }
  }

  @NotNull
  public static List<AttachItem> collectAttachHostsItems(@NotNull final Project project,
                                                         @NotNull ProgressIndicator indicator) {

    List<AttachItem> currentItems = ContainerUtil.newArrayList();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (XAttachHostProvider hostProvider : Extensions.getExtensions(XAttachHostProvider.EP)) {
      indicator.checkCanceled();
      //noinspection unchecked
      Set<XAttachHost> hosts = ContainerUtil.newHashSet(hostProvider.getAvailableHosts(project));

      for (XAttachHost host : hosts) {
        //noinspection unchecked
        currentItems.add(new AttachHostItem(hostProvider.getPresentationGroup(), false, host, project, dataHolder));
      }
    }

    //noinspection unchecked
    Collections.sort(currentItems);

    doUpdateFirstInGroup(currentItems);
    return currentItems;
  }

  @NotNull
  private static List<AttachToProcessItem> getRecentItems(@NotNull List<AttachToProcessItem> currentItems,
                                                          @NotNull XAttachHost host,
                                                          @NotNull Project project,
                                                          @NotNull UserDataHolder dataHolder) {
    final List<AttachToProcessItem> result = ContainerUtil.newArrayList();
    final List<RecentItem> recentItems = getRecentItems(host, project);

    for (int i = recentItems.size() - 1; i >= 0; i--) {
      RecentItem recentItem = recentItems.get(i);
      for (AttachToProcessItem currentItem : currentItems) {
        boolean isSuitableItem = recentItem.getGroup().equals(currentItem.getGroup()) &&
                                 recentItem.getProcessInfo().getCommandLine()
                                           .equals(currentItem.getProcessInfo().getCommandLine());

        if (!isSuitableItem) continue;

        List<XAttachDebugger> debuggers = currentItem.getDebuggers();
        int selectedDebugger = -1;
        for (int j = 0; j < debuggers.size(); j++) {
          XAttachDebugger debugger = debuggers.get(j);
          if (debugger.getDebuggerDisplayName().equals(recentItem.getDebuggerName())) {
            selectedDebugger = j;
            break;
          }
        }
        if (selectedDebugger == -1) continue;

        result.add(AttachToProcessItem.createRecentAttachItem(currentItem, result.isEmpty(), debuggers, selectedDebugger, project,
                                                              dataHolder));
      }
    }
    return result;
  }

  @NotNull
  private static List<? extends ProcessInfo> getProcessInfos(@NotNull XAttachHost<?> host) {
    try {
      return host.getProcessList();
    }
    catch (ExecutionException e) {
      Notifications.Bus.notify(new Notification("Attach to Process action",
                                                XDebuggerBundle.message("xdebugger.attach.action.items.error.title"),
                                                XDebuggerBundle.message("xdebugger.attach.action.items.error.message"),
                                                NotificationType.WARNING));
      LOG.warn("Error while getting attach items", e);

      return Collections.emptyList();
    }
  }

  @NotNull
  private static List<XAttachDebuggerProvider> getProvidersApplicableForHost(@NotNull XAttachHost host) {
    return XAttachDebuggerProvider.getAttachDebuggerProviders().stream().filter(provider -> provider.isAttachHostApplicable(host))
                                  .collect(Collectors.toList());
  }

  @NotNull
  static List<AttachToProcessItem> collectAttachProcessItems(@NotNull final Project project,
                                                               @NotNull XAttachHost host,
                                                               @NotNull ProgressIndicator indicator) {
    return doCollectAttachProcessItems(project, host, getProcessInfos(host), indicator, getProvidersApplicableForHost(host));
  }

  @NotNull
  static List<AttachToProcessItem> doCollectAttachProcessItems(@NotNull final Project project,
                                                               @NotNull XAttachHost host,
                                                               @NotNull List<? extends ProcessInfo> processInfos,
                                                               @NotNull ProgressIndicator indicator,
                                                               @NotNull List<XAttachDebuggerProvider> providers) {
    UserDataHolderBase dataHolder = new UserDataHolderBase();

    List<AttachToProcessItem> currentItems = ContainerUtil.newArrayList();

    for (ProcessInfo process : processInfos) {

      MultiMap<XAttachPresentationGroup<ProcessInfo>, XAttachDebugger> groupsWithDebuggers = new MultiMap<>();

      for (XAttachDebuggerProvider provider : providers) {
        indicator.checkCanceled();

        groupsWithDebuggers.putValues(provider.getPresentationGroup(),
                                        provider.getAvailableDebuggers(project, host, process, dataHolder));
      }

      for (XAttachPresentationGroup<ProcessInfo> group : groupsWithDebuggers.keySet()) {
        Collection<XAttachDebugger> debuggers = groupsWithDebuggers.get(group);
        if (!debuggers.isEmpty()) {
          currentItems.add(new AttachToProcessItem(group, false, host, process, new ArrayList<>(debuggers), project, dataHolder));
        }
      }
    }

    Collections.sort(currentItems);

    doUpdateFirstInGroup(currentItems);

    List<AttachToProcessItem> result = getRecentItems(currentItems, host, project, dataHolder);

    result.addAll(currentItems);
    return result;
  }

  public static void addToRecent(@NotNull Project project, @NotNull AttachToProcessItem item) {
    Map<XAttachHost, LinkedHashSet<RecentItem>> recentItems = project.getUserData(RECENT_ITEMS_KEY);

    if (recentItems == null) {
      project.putUserData(RECENT_ITEMS_KEY, recentItems = ContainerUtil.newHashMap());
    }

    XAttachHost host = item.getHost();

    LinkedHashSet<RecentItem> hostRecentItems = recentItems.get(host);

    if(hostRecentItems == null) {
      recentItems.put(host, ContainerUtil.newLinkedHashSet());
      hostRecentItems = recentItems.get(host);
    }

    final RecentItem newRecentItem = new RecentItem(host, item);

    hostRecentItems.remove(newRecentItem);

    hostRecentItems.add(newRecentItem);

    while (hostRecentItems.size() > 4) {
      hostRecentItems.remove(hostRecentItems.iterator().next());
    }
  }

  @NotNull
  public static List<RecentItem> getRecentItems(@NotNull XAttachHost host,
                                                @NotNull Project project) {
    Map<XAttachHost, LinkedHashSet<RecentItem>> recentItems = project.getUserData(RECENT_ITEMS_KEY);
    return recentItems == null || !recentItems.containsKey(host)
           ? Collections.emptyList()
           : Collections.unmodifiableList(new ArrayList<>(recentItems.get(host)));
  }

  public static class RecentItem {
    @NotNull private final XAttachHost myHost;
    @NotNull private final ProcessInfo myProcessInfo;
    @NotNull private final XAttachPresentationGroup myGroup;
    @NotNull private final String myDebuggerName;

    public RecentItem(@NotNull XAttachHost host,
                      @NotNull AttachToProcessItem item) {
      this(host, item.getProcessInfo(), item.getGroup(), item.getSelectedDebugger().getDebuggerDisplayName());
    }

    private RecentItem(@NotNull XAttachHost host,
                       @NotNull ProcessInfo info,
                       @NotNull XAttachPresentationGroup group,
                       @NotNull String debuggerName) {
      myHost = host;
      myProcessInfo = info;
      myGroup = group;
      myDebuggerName = debuggerName;
    }

    @TestOnly
    public static RecentItem createRecentItem(@NotNull XAttachHost host, @NotNull ProcessInfo info, @NotNull XAttachPresentationGroup group, @NotNull String debuggerName) {
      return new RecentItem(host, info, group, debuggerName);
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
      RecentItem item = (RecentItem)o;
      return Objects.equals(myProcessInfo.getCommandLine(), item.myProcessInfo.getCommandLine());
    }

    @Override
    public int hashCode() {

      return Objects.hash(myProcessInfo.getCommandLine());
    }
  }

  public static abstract class AttachItem<T> implements Comparable<AttachItem<T>> {

    @NotNull
    XAttachPresentationGroup<T> myGroup;
    boolean myIsFirstInGroup;
    @NotNull
    String myGroupName;
    @NotNull
    Project myProject;
    @NotNull
    UserDataHolder myDataHolder;
    @NotNull
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

    void makeFirstInGroup() {
      myIsFirstInGroup = true;
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
      return myGroup.getItemIcon(project, myInfo, myDataHolder);
    }

    abstract boolean hasSubStep();

    abstract String getText(@NotNull Project project);

    abstract List<AttachToProcessItem> getSubItems();

    @Override
    public int compareTo(AttachItem<T> compareItem) {
      int groupDifference = myGroup.getOrder() - compareItem.getGroup().getOrder();

      if(groupDifference != 0) {
        return groupDifference;
      }

      return myGroup.compare(myInfo, compareItem.myInfo);
    }
  }

  private static class AttachHostItem extends AttachItem<XAttachHost> {

    public AttachHostItem(@NotNull XAttachPresentationGroup<XAttachHost> group,
                          boolean isFirstInGroup,
                          @NotNull XAttachHost host,
                          @NotNull Project project,
                          @NotNull UserDataHolder dataHolder) {
      super(group, isFirstInGroup, group.getGroupName(), host, project, dataHolder);
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
      return collectAttachProcessItems(myProject, myInfo, new EmptyProgressIndicator());
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

    static AttachToProcessItem createRecentAttachItem(AttachToProcessItem item,
                                                             boolean isFirstInGroup,
                                                             List<XAttachDebugger> debuggers,
                                                             int selectedDebugger,
                                                             Project project, UserDataHolder dataHolder) {
      return new AttachToProcessItem(item.getGroup(), isFirstInGroup, XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent"),
                                     item.getHost(), item.getProcessInfo(), debuggers, selectedDebugger, project, dataHolder);
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
          addToRecent(myProject, attachToProcessItem);
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
          public PopupStep call() {
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
        addToRecent(myProject, selectedValue);
        return doFinalStep(() -> selectedValue.startDebugSession(myProject));
      }
    }
  }
}
