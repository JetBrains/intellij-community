package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefresher;
import com.intellij.vcs.log.VcsLogSettings;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.ui.VcsLogColorManagerImpl;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

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
  private volatile VcsLogUiImpl myUi;

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

    Consumer<DataPack> dataPackUpdateHandler = new Consumer<DataPack>() {
      @Override
      public void consume(final DataPack dataPack) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            if (!Disposer.isDisposed(myUi)) {
              myUi.setDataPack(dataPack);
              myProject.getMessageBus().syncPublisher(VcsLogDataHolder.REFRESH_COMPLETED).refresh(dataPack);
            }
          }
        });
      }
    };
    VcsLogDataHolder logDataHolder = new VcsLogDataHolder(myProject, this, logProviders, mySettings, dataPackUpdateHandler);
    myUi = new VcsLogUiImpl(logDataHolder, myProject, mySettings,
                            new VcsLogColorManagerImpl(logProviders.keySet()), myUiProperties, EmptyDataPack.getInstance());
    myLogRefresher = new PostponeableLogRefresher(myProject, logDataHolder);
    refreshLogOnVcsEvents(logProviders);
    logDataHolder.initialize();

    // todo fix selection
    final VcsLogGraphTable graphTable = myUi.getTable();
    if (graphTable.getRowCount() > 0) {
      IdeFocusManager.getInstance(myProject).requestFocus(graphTable, true).doWhenProcessed(new Runnable() {
        @Override
        public void run() {
          graphTable.setRowSelectionInterval(0, 0);
        }
      });
    }
    return myUi.getMainFrame().getMainComponent();
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
   * The instance of the {@link com.intellij.vcs.log.ui.VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getLogUi() {
    return myUi;
  }

  @Override
  public void dispose() {
  }

  private static class PostponeableLogRefresher implements VcsLogRefresher, Disposable {

    private  static final String TOOLWINDOW_ID = ChangesViewContentManager.TOOLWINDOW_ID;

    @NotNull private final VcsLogDataHolder myDataHolder;
    @NotNull private final ToolWindowManagerImpl myToolWindowManager;
    @NotNull private final ToolWindowImpl myToolWindow;
    @NotNull private final MyRefreshPostponedEventsListener myPostponedEventsListener;

    @NotNull private final Set<VirtualFile> myRootsToRefresh = new ConcurrentHashSet<VirtualFile>();

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
        myDataHolder.refresh(Collections.singleton(root));
      }
      else {
        myRootsToRefresh.add(root);
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
      Set<VirtualFile> toRefresh = new HashSet<VirtualFile>(myRootsToRefresh);
      myRootsToRefresh.removeAll(toRefresh); // clear the set, but keep roots which could possibly arrive after collecting them in the var.
      myDataHolder.refresh(toRefresh);
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
