// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.popup.async.AsyncPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupWrapper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.StatusText;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.attach.*;
import com.intellij.xdebugger.impl.ui.attach.dialog.AttachToProcessDialogFactory;
import org.jetbrains.annotations.*;
import org.jetbrains.concurrency.AsyncPromise;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@ApiStatus.Internal
public abstract class AttachToProcessActionBase extends AnAction implements DumbAware {
  private static final Key<Map<XAttachHost, LinkedHashSet<RecentItem>>> RECENT_ITEMS_KEY =
    Key.create("AttachToProcessAction.RECENT_ITEMS_KEY");
  private static final Logger LOG = Logger.getInstance(AttachToProcessActionBase.class);

  private final @NotNull Supplier<? extends List<XAttachDebuggerProvider>> myAttachProvidersSupplier;
  private final @NotNull @NlsContexts.PopupTitle String myAttachActionsListTitle;

  public AttachToProcessActionBase(@Nullable @NlsActions.ActionText String text,
                                   @Nullable @NlsActions.ActionDescription String description,
                                   @Nullable Icon icon,
                                   @NotNull Supplier<? extends List<XAttachDebuggerProvider>> attachProvidersSupplier,
                                   @NotNull @NlsContexts.PopupTitle String attachActionsListTitle) {
    super(text, description, icon);
    myAttachProvidersSupplier = attachProvidersSupplier;
    myAttachActionsListTitle = attachActionsListTitle;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {

    Project project = getEventProject(e);
    int attachDebuggerProvidersNumber = myAttachProvidersSupplier.get().size();
    boolean enabled = project != null && attachDebuggerProvidersNumber > 0;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    if (project == null) return;

    if (Registry.is("debugger.attach.dialog.enabled")) {
      project.getService(AttachToProcessDialogFactory.class).showDialog(
        myAttachProvidersSupplier.get(),
        getAvailableHosts(),
        e.getDataContext());
      return;
    }

    new Task.Backgroundable(project,
                            XDebuggerBundle.message("xdebugger.attach.action.collectingItems"), true,
                            PerformInBackgroundOption.DEAF) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {

        List<AttachItem> allItems = Collections.unmodifiableList(getTopLevelItems(indicator, project));

        ApplicationManager.getApplication().invokeLater(() -> {
          AttachListStep step = new AttachListStep(allItems, XDebuggerBundle.message("xdebugger.attach.popup.title.default"), project);

          final ListPopup popup = JBPopupFactory.getInstance().createListPopup(step);
          final JList<?> mainList = ((ListPopupImpl) ListPopupWrapper.getRootPopup(popup)).getList();

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

            if (item instanceof AttachHostItem hostItem) {
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

  protected @Unmodifiable List<XAttachHostProvider<XAttachHost>> getAvailableHosts() {
    return ContainerUtil.map(XAttachHostProvider.EP.getExtensionList(), provider -> (XAttachHostProvider<XAttachHost>)provider);
  }

  protected @NotNull List<? extends AttachItem> getTopLevelItems(@NotNull ProgressIndicator indicator, @NotNull Project project) {
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

  public @NotNull List<AttachItem> collectAttachHostsItems(final @NotNull Project project,
                                                           @NotNull ProgressIndicator indicator) {

    List<AttachItem> currentItems = new ArrayList<>();

    UserDataHolderBase dataHolder = new UserDataHolderBase();

    for (XAttachHostProvider hostProvider : getAvailableHosts()) {
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

  public static @NotNull List<AttachToProcessItem> getRecentItems(@NotNull List<? extends AttachToProcessItem> currentItems,
                                                                  @NotNull XAttachHost host,
                                                                  @NotNull Project project,
                                                                  @NotNull UserDataHolder dataHolder) {
    final List<AttachToProcessItem> result = new ArrayList<>();
    final List<RecentItem> recentItems = getRecentItems(host, project);

    for (int i = recentItems.size() - 1; i >= 0; i--) {
      RecentItem recentItem = recentItems.get(i);
      result.addAll(ApplicationManager.getApplication().getService(XAttachRecentItemsMatcher.class)
                      .getMatchingAttachItems(recentItem, currentItems, result.isEmpty(), project, dataHolder));
    }
    return result;
  }

  private static @NotNull List<ProcessInfo> getProcessInfos(@NotNull XAttachHost host) {
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

  private @NotNull @Unmodifiable List<XAttachDebuggerProvider> getProvidersApplicableForHost(@NotNull XAttachHost host) {
    return ContainerUtil.filter(myAttachProvidersSupplier.get(), provider -> provider.isAttachHostApplicable(host));
  }

  public @NotNull List<AttachToProcessItem> collectAttachProcessItems(final @NotNull Project project,
                                                                      @NotNull XAttachHost host,
                                                                      @NotNull ProgressIndicator indicator) {
    return doCollectAttachProcessItems(project, host, getProcessInfos(host), indicator, getProvidersApplicableForHost(host));
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull List<AttachToProcessItem> doCollectAttachProcessItems(final @NotNull Project project,
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

  public static @NotNull List<RecentItem> getRecentItems(@NotNull XAttachHost host,
                                                         @NotNull Project project) {
    Map<XAttachHost, LinkedHashSet<RecentItem>> recentItems = project.getUserData(RECENT_ITEMS_KEY);
    return recentItems == null || !recentItems.containsKey(host)
           ? Collections.emptyList()
           : List.copyOf(recentItems.get(host));
  }

  public static final class RecentItem {
    private final @NotNull XAttachHost myHost;
    private final @NotNull ProcessInfo myProcessInfo;
    private final @NotNull XAttachPresentationGroup myGroup;
    private final @NotNull String myDebuggerName;
    private final @NotNull Instant myRecentItemCreationTime = Instant.now();

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

    public @NotNull XAttachHost getHost() {
      return myHost;
    }

    public @NotNull ProcessInfo getProcessInfo() {
      return myProcessInfo;
    }

    public @NotNull XAttachPresentationGroup getGroup() {
      return myGroup;
    }

    public @NotNull String getDebuggerName() {
      return myDebuggerName;
    }

    @SuppressWarnings("unused")
    public @NotNull Instant getRecentItemCreationTime() {
      return myRecentItemCreationTime;
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

  public abstract static class AttachItem<T> implements Comparable<AttachItem<T>> {

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

    public @NotNull XAttachPresentationGroup<T> getGroup() {
      return myGroup;
    }

    @Nullable @NlsContexts.Separator
    @VisibleForTesting
    @ApiStatus.Internal
    public String getSeparatorTitle() {
      return myIsFirstInGroup ? myGroupName : null;
    }

    @ApiStatus.Internal
    public @NotNull UserDataHolder getDataHolder() {
      return myDataHolder;
    }

    protected @Nullable Icon getIcon(@NotNull Project project) {
      return myGroup.getItemIcon(project, myInfo, myDataHolder);
    }

    protected abstract boolean hasSubStep();

    protected abstract @Nls String getText(@NotNull Project project);

    protected abstract @Nullable @NlsContexts.Tooltip String getTooltipText(@NotNull Project project);

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

    @Override
    public @NotNull String getText(@NotNull Project project) {
      return myGroup.getItemDisplayText(project, myInfo, myDataHolder);
    }

    @Override
    public @Nullable String getTooltipText(@NotNull Project project) {
      return myGroup.getItemDescription(project, myInfo, myDataHolder);
    }

    @Override
    public List<AttachToProcessItem> getSubItems() {
      return collectAttachProcessItems(myProject, myInfo, new EmptyProgressIndicator());
    }
  }

  public static class AttachToProcessItem extends AttachItem<ProcessInfo> {
    private final @NotNull List<XAttachDebugger> myDebuggers;
    private final int mySelectedDebugger;
    private final @NotNull List<AttachToProcessItem> mySubItems;
    private final @NotNull XAttachHost myHost;

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

    public static AttachToProcessItem createRecentAttachItem(AttachToProcessItem item,
                                                      boolean isFirstInGroup,
                                                      List<XAttachDebugger> debuggers,
                                                      int selectedDebugger,
                                                      Project project, UserDataHolder dataHolder) {
      return new AttachToProcessItem(item.getGroup(), isFirstInGroup, XDebuggerBundle.message("xdebugger.attach.toLocal.popup.recent"),
                                     item.getHost(), item.getProcessInfo(), debuggers, selectedDebugger, project, dataHolder);
    }

    public @NotNull ProcessInfo getProcessInfo() {
      return myInfo;
    }

    public @NotNull XAttachHost getHost() {
      return myHost;
    }

    @Override
    public boolean hasSubStep() {
      return !mySubItems.isEmpty();
    }

    @Override
    public @Nullable String getTooltipText(@NotNull Project project) {
      return myGroup.getItemDescription(project, myInfo, myDataHolder);
    }

    @Override
    public @NotNull String getText(@NotNull Project project) {
      String shortenedText = StringUtil.shortenTextWithEllipsis(myGroup.getItemDisplayText(project, myInfo, myDataHolder), 200, 0);
      int pid = myInfo.getPid();
      return (pid == -1 ? "" : pid + " ") + shortenedText;
    }

    public @NotNull List<XAttachDebugger> getDebuggers() {
      return myDebuggers;
    }

    @Override
    public @NotNull List<AttachToProcessItem> getSubItems() {
      return mySubItems;
    }

    public @NotNull XAttachDebugger getSelectedDebugger() {
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

  @ApiStatus.Internal
  public static class MyBasePopupStep<T extends AttachItem> extends BaseListPopupStep<T> {
    final @NotNull Project myProject;

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
    public PopupStep<?> onChosen(T selectedValue, boolean finalChoice) {
      return null;
    }
  }

  public final class AttachListStep extends MyBasePopupStep<AttachItem> implements ListPopupStepEx<AttachItem> {
    public AttachListStep(@NotNull List<AttachItem> items, @Nullable @NlsContexts.PopupTitle String title, @NotNull Project project) {
      super(project, title, items);
    }

    @Override
    public @Nullable ListSeparator getSeparatorAbove(AttachItem value) {
      String separatorTitle = value.getSeparatorTitle();
      return separatorTitle == null ? null : new ListSeparator(separatorTitle);
    }

    @Override
    public Icon getIconFor(AttachItem value) {
      return value.getIcon(myProject);
    }

    @Override
    public @NotNull String getTextFor(AttachItem value) {
      return value.getText(myProject);
    }

    @Override
    public boolean hasSubstep(AttachItem selectedValue) {
      return selectedValue.hasSubStep();
    }

    @Override
    public @Nullable String getTooltipTextFor(AttachItem value) {
      return value.getTooltipText(myProject);
    }

    @Override
    public void setEmptyText(@NotNull StatusText emptyText) {
      emptyText.setText(XDebuggerBundle.message("xdebugger.attach.popup.emptyText"));
    }

    @Override
    public PopupStep<?> onChosen(AttachItem selectedValue, boolean finalChoice) {
      if (selectedValue instanceof AttachToProcessItem attachToProcessItem) {
        if (finalChoice) {
          addToRecent(myProject, attachToProcessItem);
          return doFinalStep(() -> attachToProcessItem.startDebugSession(myProject));
        }
        else {
          return new ActionListStep(attachToProcessItem.getSubItems(), attachToProcessItem.mySelectedDebugger);
        }
      }

      if (selectedValue instanceof AttachHostItem attachHostItem) {
        AsyncPromise<PopupStep<AttachItem>> promise = new AsyncPromise<>();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          List<AttachItem> attachItems = new ArrayList<>(attachHostItem.getSubItems());
          ApplicationManager.getApplication().invokeLater(() -> promise.setResult(new AttachListStep(attachItems, null, myProject)));
        });
        return new AsyncPopupStep<>(promise);
      }
      return null;
    }

    @Override
    public boolean isFinal(AttachItem value) {
      return value instanceof AttachToProcessItem;
    }

    private class ActionListStep extends MyBasePopupStep<AttachToProcessItem> {
      ActionListStep(List<AttachToProcessItem> items, int selectedItem) {
        super(AttachListStep.this.myProject, AttachToProcessActionBase.this.myAttachActionsListTitle, items);
        setDefaultOptionIndex(selectedItem);
      }

      @Override
      public @NotNull String getTextFor(AttachToProcessItem value) {
        return value.getSelectedDebugger().getDebuggerDisplayName();
      }

      @Override
      public PopupStep<?> onChosen(AttachToProcessItem selectedValue, boolean finalChoice) {
        addToRecent(myProject, selectedValue);
        return doFinalStep(() -> selectedValue.startDebugSession(myProject));
      }
    }
  }
}
