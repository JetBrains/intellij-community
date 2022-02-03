// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.function.Supplier;

public abstract class AttachToProcessActionBase extends AnAction implements DumbAware {
  private static final Key<Map<XAttachHost, LinkedHashSet<RecentItem>>> RECENT_ITEMS_KEY =
    Key.create("AttachToProcessAction.RECENT_ITEMS_KEY");
  private static final Logger LOG = Logger.getInstance(AttachToProcessActionBase.class);

  @NotNull
  private final Supplier<? extends List<XAttachDebuggerProvider>> myAttachProvidersSupplier;
  @NotNull
  private final @NlsContexts.PopupTitle String myAttachActionsListTitle;
  @NotNull
  private final Supplier<? extends List<XAttachHostProvider>> myAttachHostProviderSupplier;

  public AttachToProcessActionBase(@Nullable @NlsActions.ActionText String text,
                                   @Nullable @NlsActions.ActionDescription String description,
                                   @Nullable Icon icon,
                                   @NotNull Supplier<? extends List<XAttachDebuggerProvider>> attachProvidersSupplier,
                                   @NotNull Supplier<? extends List<XAttachHostProvider>> attachHostProviderSupplier,
                                   @NotNull @NlsContexts.PopupTitle String attachActionsListTitle) {
    super(text, description, icon);
    myAttachProvidersSupplier = attachProvidersSupplier;
    myAttachActionsListTitle = attachActionsListTitle;
    myAttachHostProviderSupplier = attachHostProviderSupplier;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {

    Project project = getEventProject(e);
    int attachDebuggerProvidersNumber = myAttachProvidersSupplier.get().size();
    boolean enabled = project != null && attachDebuggerProvidersNumber > 0;
    e.getPresentation().setEnabledAndVisible(enabled);
  }


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;


    new Task.Backgroundable(project,
                            XDebuggerBundle.message("xdebugger.attach.action.collectingItems"), true,
                            PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {

        List<AttachItem> allItems = ContainerUtil.immutableList(getTopLevelItems(indicator, project));

        ApplicationManager.getApplication().invokeLater(() -> {
          AttachListStep step = new AttachListStep(allItems, XDebuggerBundle.message("xdebugger.attach.popup.title.default"), project);

          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
          final JList mainList = ((ListPopupImpl)popup).getList();

          ListSelectionListener listener = event -> {
            if (event.getValueIsAdjusting()) return;

            Object item = ((JList<?>)event.getSource()).getSelectedValue();

            // if a sub-list is closed, fallback to the selected value from the main list
            if (item == null) {
              item = mainList.getSelectedValue();
            }

            if (item instanceof AttachToProcessItem) {
              popup.setCaption(((AttachToProcessItem)item).getSelectedDebugger().getDebuggerSelectedTitle());
            }

            if (item instanceof AttachHostItem) {
              AttachHostItem hostItem = (AttachHostItem)item;
              String attachHostName = hostItem.getText(project);
              attachHostName = StringUtil.shortenTextWithEllipsis(attachHostName, 50, 0);

              popup.setCaption(XDebuggerBundle.message("xdebugger.attach.host.popup.title", attachHostName));
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
  protected List<? extends AttachItem> getTopLevelItems(@NotNull ProgressIndicator indicator, @NotNull Project project) {
    List<AttachItem> attachHostItems = collectAttachHostsItems(project, indicator);

    // If any of hosts available, fold local PIDs into "Local Host" subgroup
    if (!attachHostItems.isEmpty()) {
      AttachItem localHostGroupItem = new AttachHostItem(
        LocalAttachHostPresentationGroup.INSTANCE, false, LocalAttachHost.INSTANCE, project, new UserDataHolderBase());
      attachHostItems.add(localHostGroupItem);
      doUpdateFirstInGroup(attachHostItems);
      return attachHostItems;
    }
    return collectAttachProcessItems(project, LocalAttachHost.INSTANCE, indicator);
  }

  private static void doUpdateFirstInGroup(@NotNull List<? extends AttachItem> items) {
    if (items.isEmpty()) {
      return;
    }

    items.get(0).makeFirstInGroup();

    for (int i = 1; i < items.size(); i++) {
      if (items.get(i).getGroup() != items.get(i - 1).getGroup()) {
        items.get(i).makeFirstInGroup();
      }
    }
  }

  @NotNull
  public List<AttachItem> collectAttachHostsItems(@NotNull final Project project,
                                                  @NotNull ProgressIndicator indicator) {

    List<AttachItem> currentItems = new ArrayList<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (XAttachHostProvider hostProvider : myAttachHostProviderSupplier.get()) {
      indicator.checkCanceled();
      //noinspection unchecked
      Set<XAttachHost> hosts = new HashSet<>(hostProvider.getAvailableHosts(project));

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
  private static List<AttachToProcessItem> getRecentItems(@NotNull List<? extends AttachToProcessItem> currentItems,
                                                          @NotNull XAttachHost host,
                                                          @NotNull Project project,
                                                          @NotNull UserDataHolder dataHolder) {
    final List<AttachToProcessItem> result = new ArrayList<>();
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
  private static List<ProcessInfo> getProcessInfos(@NotNull XAttachHost host) {
    try {
      return host.getProcessList();
    }
    catch (ExecutionException e) {
      Notifications.Bus.notify(new Notification(
        "Attach to Process action",
        XDebuggerBundle.message("xdebugger.attach.action.items.error.title"),
        XDebuggerBundle.message("xdebugger.attach.action.items.error.message"),
        NotificationType.WARNING));
      LOG.warn("Error getting process list from the host " + host + ": " + e.getMessage());

      return Collections.emptyList();
    }
  }

  @NotNull
  private List<XAttachDebuggerProvider> getProvidersApplicableForHost(@NotNull XAttachHost host) {
    return ContainerUtil.filter(myAttachProvidersSupplier.get(), provider -> provider.isAttachHostApplicable(host));
  }

  @NotNull
  public List<AttachToProcessItem> collectAttachProcessItems(@NotNull final Project project,
                                                             @NotNull XAttachHost host,
                                                             @NotNull ProgressIndicator indicator) {
    return doCollectAttachProcessItems(project, host, getProcessInfos(host), indicator, getProvidersApplicableForHost(host));
  }

  @NotNull
  static List<AttachToProcessItem> doCollectAttachProcessItems(@NotNull final Project project,
                                                               @NotNull XAttachHost host,
                                                               @NotNull List<? extends ProcessInfo> processInfos,
                                                               @NotNull ProgressIndicator indicator,
                                                               @NotNull List<? extends XAttachDebuggerProvider> providers) {
    UserDataHolderBase dataHolder = new UserDataHolderBase();

    List<AttachToProcessItem> currentItems = new ArrayList<>();

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
      project.putUserData(RECENT_ITEMS_KEY, recentItems = new HashMap<>());
    }

    XAttachHost host = item.getHost();

    LinkedHashSet<RecentItem> hostRecentItems = recentItems.get(host);

    if (hostRecentItems == null) {
      recentItems.put(host, new LinkedHashSet<>());
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
    public static RecentItem createRecentItem(@NotNull XAttachHost host,
                                              @NotNull ProcessInfo info,
                                              @NotNull XAttachPresentationGroup group,
                                              @NotNull String debuggerName) {
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
    @NotNull @NlsContexts.Separator
    String myGroupName;
    @NotNull
    Project myProject;
    @NotNull
    UserDataHolder myDataHolder;
    @NotNull
    T myInfo;

    public AttachItem(@NotNull XAttachPresentationGroup<T> group,
                      boolean isFirstInGroup,
                      @NotNull @NlsContexts.Separator String groupName,
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
    public XAttachPresentationGroup<T> getGroup() {
      return myGroup;
    }

    @Nullable @NlsContexts.Separator
    String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroupName : null;
    }

    @Nullable
    protected Icon getIcon(@NotNull Project project) {
      return myGroup.getItemIcon(project, myInfo, myDataHolder);
    }

    protected abstract boolean hasSubStep();

    @Nls
    protected abstract String getText(@NotNull Project project);

    @Nullable
    protected abstract @NlsContexts.Tooltip String getTooltipText(@NotNull Project project);

    protected abstract List<AttachToProcessItem> getSubItems();

    @Override
    public int compareTo(AttachItem<T> compareItem) {
      int groupDifference = myGroup.getOrder() - compareItem.getGroup().getOrder();

      if (groupDifference != 0) {
        return groupDifference;
      }

      return myGroup.compare(myInfo, compareItem.myInfo);
    }
  }

  private class AttachHostItem extends AttachItem<XAttachHost> {

    AttachHostItem(@NotNull XAttachPresentationGroup<XAttachHost> group,
                   boolean isFirstInGroup,
                   @NotNull XAttachHost host,
                   @NotNull Project project,
                   @NotNull UserDataHolder dataHolder) {
      super(group, isFirstInGroup, group.getGroupName(), host, project, dataHolder);
    }

    @Override
    public boolean hasSubStep() {
      return true;
    }

    @NotNull
    @Override
    public String getText(@NotNull Project project) {
      return myGroup.getItemDisplayText(project, myInfo, myDataHolder);
    }

    @Override
    @Nullable
    public String getTooltipText(@NotNull Project project) {
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
                               @NotNull @Nls String groupName,
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
               debugger -> new AttachToProcessItem(myGroup, false, myHost, myInfo, Collections.singletonList(debugger), myProject,
                                                   dataHolder));
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

    @Override
    public boolean hasSubStep() {
      return !mySubItems.isEmpty();
    }

    @Override
    @Nullable
    public String getTooltipText(@NotNull Project project) {
      return myGroup.getItemDescription(project, myInfo, myDataHolder);
    }

    @Override
    @NotNull
    public String getText(@NotNull Project project) {
      String shortenedText = StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, myInfo, myDataHolder), 200, 0);
      int pid = myInfo.getPid();
      return (pid == -1 ? "" : pid + " ") + shortenedText;
    }

    @NotNull
    public List<XAttachDebugger> getDebuggers() {
      return myDebuggers;
    }

    @Override
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

      try {
        debugger.attachDebugSession(project, myHost, myInfo);
      }
      catch (ExecutionException e) {
        final String message = XDebuggerBundle.message("xdebugger.attach.pid", myInfo.getPid());
        ExecutionUtil.handleExecutionError(project, ToolWindowId.DEBUG, message, e);
      }
    }
  }

  private static class MyBasePopupStep<T extends AttachItem> extends BaseListPopupStep<T> {
    @NotNull final Project myProject;

    MyBasePopupStep(@NotNull Project project,
                    @Nullable @NlsContexts.PopupTitle String title,
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

  public class AttachListStep extends MyBasePopupStep<AttachItem> implements ListPopupStepEx<AttachItem> {
    public AttachListStep(@NotNull List<AttachItem> items, @Nullable @NlsContexts.PopupTitle String title, @NotNull Project project) {
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
      return value.getTooltipText(myProject);
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
          return new ActionListStep(attachToProcessItem.getSubItems(), attachToProcessItem.mySelectedDebugger);
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
    public boolean isFinal(AttachItem value) {
      return value instanceof AttachToProcessItem;
    }

    @Override
    public PopupStep onChosen(AttachItem selectedValue,
                              boolean finalChoice,
                              @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers) {
      return onChosen(selectedValue, finalChoice);
    }

    private class ActionListStep extends MyBasePopupStep<AttachToProcessItem> {
      ActionListStep(List<AttachToProcessItem> items, int selectedItem) {
        super(AttachListStep.this.myProject, AttachToProcessActionBase.this.myAttachActionsListTitle, items);
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
