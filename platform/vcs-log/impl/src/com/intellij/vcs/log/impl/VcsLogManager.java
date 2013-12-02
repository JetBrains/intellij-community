package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VcsLogUiProperties;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogManager implements Disposable {

  public static final ExtensionPointName<VcsLogProvider> LOG_PROVIDER_EP = ExtensionPointName.create("com.intellij.logProvider");

  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final VcsLogSettings mySettings;
  @NotNull private final VcsLogUiProperties myUiProperties;

  private PostponeableLogRefresher myLogRefresher;
  private volatile VcsLogDataHolder myLogDataHolder;
  private volatile VcsLogUI myUi;

  public VcsLogManager(@NotNull Project project, @NotNull ProjectLevelVcsManager vcsManager,
                       @NotNull VcsLogSettings settings,
                       @NotNull VcsLogUiProperties uiProperties) {
    myProject = project;
    myVcsManager = vcsManager;
    mySettings = settings;
    myUiProperties = uiProperties;
    Disposer.register(myProject, this);
  }

  @NotNull
  public JComponent initContent() {
    final Map<VirtualFile, VcsLogProvider> logProviders = findLogProviders();
    final VcsLogContainer mainPanel = new VcsLogContainer(myProject);

    myLogDataHolder = new VcsLogDataHolder(myProject, logProviders, mySettings);
    myLogDataHolder.initialize(new Consumer<VcsLogDataHolder>() {
      @Override
      public void consume(VcsLogDataHolder vcsLogDataHolder) {
        Disposer.register(VcsLogManager.this, vcsLogDataHolder);
        VcsLogUI logUI = new VcsLogUI(vcsLogDataHolder, myProject, mySettings,
                                      new VcsLogColorManagerImpl(logProviders.keySet()), myUiProperties);
        myLogDataHolder = vcsLogDataHolder;
        myUi = logUI;
        mainPanel.init(logUI.getMainFrame().getMainComponent());
        myLogRefresher = new PostponeableLogRefresher(myProject, vcsLogDataHolder);
        refreshLogOnVcsEvents(logProviders);
      }
    });
    return mainPanel;
  }

  private void refreshLogOnVcsEvents(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
    MultiMap<VcsLogProvider, VirtualFile> providers2roots = MultiMap.create();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : logProviders.entrySet()) {
      providers2roots.putValue(entry.getValue(), entry.getKey());
    }

    for (Map.Entry<VcsLogProvider, Collection<VirtualFile>> entry : providers2roots.entrySet()) {
      entry.getKey().subscribeToRootRefreshEvents(entry.getValue(), myLogRefresher);
    }
  }

  @NotNull
  public Map<VirtualFile, VcsLogProvider> findLogProviders() {
    Map<VirtualFile, VcsLogProvider> logProviders = ContainerUtil.newHashMap();
    VcsLogProvider[] allLogProviders = Extensions.getExtensions(LOG_PROVIDER_EP, myProject);
    for (AbstractVcs vcs : myVcsManager.getAllActiveVcss()) {
      for (VcsLogProvider provider : allLogProviders) {
        if (provider.getSupportedVcs().equals(vcs.getKeyInstanceMethod())) {
          for (VirtualFile root : myVcsManager.getRootsUnderVcs(vcs)) {
            logProviders.put(root, provider);
          }
          break;
        }
      }
    }
    return logProviders;
  }

  /**
   * The instance of the {@link VcsLogDataHolder} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogDataHolder getDataHolder() {
    return myLogDataHolder;
  }

  /**
   * The instance of the {@link VcsLogUI} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUI getLogUi() {
    return myUi;
  }

  @Override
  public void dispose() {
  }

  private class VcsLogContainer extends JPanel implements TypeSafeDataProvider {

    private final JBLoadingPanel myLoadingPanel;

    VcsLogContainer(@NotNull Disposable disposable) {
      setLayout(new BorderLayout());
      myLoadingPanel = new JBLoadingPanel(new BorderLayout(), disposable);
      add(myLoadingPanel);
      myLoadingPanel.startLoading();
    }

    void init(@NotNull JComponent mainComponent) {
      myLoadingPanel.add(mainComponent);
      myLoadingPanel.stopLoading();
    }

    @Override
    public void calcData(DataKey key, DataSink sink) {
      if (myUi != null) {
        myUi.getMainFrame().calcData(key, sink);
      }
    }

  }

  private static class PostponeableLogRefresher implements VcsLogRefresher, Disposable {

    private  static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

    @NotNull private final VcsLogDataHolder myDataHolder;
    @NotNull private final ToolWindowManagerImpl myToolWindowManager;
    @NotNull private final ToolWindowImpl myToolWindow;
    @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;

    @NotNull private final Set<VirtualFile> myRootsToRefreshRefs = ContainerUtil.newHashSet();
    @NotNull private final Set<VirtualFile> myRootsToRefresh = ContainerUtil.newHashSet();
    @NotNull private final Object REFRESH_LOCK = new Object();

    public PostponeableLogRefresher(@NotNull Project project, @NotNull VcsLogDataHolder dataHolder) {
      myDataHolder = dataHolder;
      myToolWindowManager = (ToolWindowManagerImpl)ToolWindowManager.getInstance(project);
      myToolWindow = (ToolWindowImpl)myToolWindowManager.getToolWindow(TOOLWINDOW_ID);

      Disposer.register(myToolWindow.getContentManager(), this);

      myPostponedEventsListener = new MyRefreshPostponedEventsListener();
      myToolWindow.getContentManager().addContentManagerListener(myPostponedEventsListener);
      myToolWindowManager.addToolWindowManagerListener(myPostponedEventsListener);
    }

    @Override
    public void refresh(@NotNull VirtualFile root) {
      if (isOurContentPaneShowing()) {
        myDataHolder.refresh(root);
      }
      else {
        synchronized (REFRESH_LOCK) {
          myRootsToRefresh.add(root);
        }
      }
    }

    @Override
    public void refreshRefs(@NotNull VirtualFile root) {
      if (isOurContentPaneShowing()) {
        myDataHolder.refreshRefs(root);
      }
      else {
        synchronized (REFRESH_LOCK) {
          myRootsToRefreshRefs.add(root);
        }
      }
    }

    @Override
    public void dispose() {
      myToolWindow.getContentManager().removeContentManagerListener(myPostponedEventsListener);
      myToolWindowManager.removeToolWindowManagerListener(myPostponedEventsListener);
    }

    private boolean isOurContentPaneShowing() {
      if (myToolWindowManager.isToolWindowRegistered(TOOLWINDOW_ID) && myToolWindow.isVisible()) {
        Content content = myToolWindow.getContentManager().getSelectedContent();
        return content != null && content.getTabName().equals(VcsLogContentProvider.TAB_NAME);
      }
      return false;
    }

    private void refreshPostponedRoots() {
      for (VirtualFile root : safeGetAndClear(myRootsToRefresh)) {
        myDataHolder.refresh(root); // TODO support batch root refresh
      }
      for (VirtualFile root : safeGetAndClear(myRootsToRefreshRefs)) {
        myDataHolder.refreshRefs(root); // TODO support batch root refresh
      }
    }

    @NotNull
    private Set<VirtualFile> safeGetAndClear(@NotNull Set<VirtualFile> unsafeRefs) {
      Set<VirtualFile> safeRefs = ContainerUtil.newHashSet();
      synchronized (REFRESH_LOCK) {
        safeRefs.addAll(unsafeRefs);
        unsafeRefs.clear();
      }
      return safeRefs;
    }

    private class MyRefreshPostponedEventsListener extends ContentManagerAdapter implements ToolWindowManagerListener {

      @Override
      public void selectionChanged(ContentManagerEvent event) {
        refreshRootsIfNeeded();
      }

      @Override
      public void stateChanged() {
        refreshRootsIfNeeded();
      }

      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      private void refreshRootsIfNeeded() {
        if (isOurContentPaneShowing()) {
          refreshPostponedRoots();
        }
      }
    }
  }

}
