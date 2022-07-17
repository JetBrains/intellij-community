// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public final class VcsLogContentProvider implements ChangesViewContentProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogContentProvider.class);
  @NonNls public static final String TAB_NAME = "Log"; // used as tab id, not user-visible
  @NonNls public static final String MAIN_LOG_ID = "MAIN";

  @NotNull private final VcsProjectLog myProjectLog;
  @NotNull private final JPanel myContainer = new JBPanel<>(new BorderLayout());
  @Nullable private SettableFuture<MainVcsLogUi> myLogCreationCallback;

  @Nullable private MainVcsLogUi myUi;
  @Nullable private Content myContent;

  public VcsLogContentProvider(@NotNull Project project) {
    myProjectLog = VcsProjectLog.getInstance(project);

    MessageBusConnection connection = project.getMessageBus().connect(myProjectLog);
    connection.subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager logManager) {
        addMainUi(logManager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager logManager) {
        disposeMainUi();
      }
    });

    VcsLogManager manager = myProjectLog.getLogManager();
    if (manager != null) {
      addMainUi(manager);
    }
  }

  public @Nullable MainVcsLogUi getUi() {
    return myUi;
  }

  @Override
  public void initTabContent(@NotNull Content content) {
    myContent = content;
    // Display name is always used for presentation, tab name is used as an id.
    // See com.intellij.vcs.log.impl.VcsLogContentUtil.selectMainLog.
    myContent.setTabName(TAB_NAME); //NON-NLS
    updateDisplayName();

    myProjectLog.createLogInBackground(true);

    content.setComponent(myContainer);
    content.setDisposer(() -> {
      disposeContent();
      myContent = null;
    });
  }

  @RequiresEdt
  private void addMainUi(@NotNull VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (myUi == null) {
      myUi = logManager.createLogUi(MAIN_LOG_ID, VcsLogTabLocation.TOOL_WINDOW, false);
      VcsLogPanel panel = new VcsLogPanel(logManager, myUi);
      myContainer.add(panel, BorderLayout.CENTER);
      DataManager.registerDataProvider(myContainer, panel);

      updateDisplayName();
      myUi.getFilterUi().addFilterListener(this::updateDisplayName);

      if (myLogCreationCallback != null) {
        myLogCreationCallback.set(myUi);
        myLogCreationCallback = null;
      }
    }
  }

  private void updateDisplayName() {
    if (myContent != null && myUi != null) {
      myContent.setDisplayName(VcsLogTabsManager.generateDisplayName(myUi));
    }
  }

  @RequiresEdt
  private void disposeMainUi() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    myContainer.removeAll();
    DataManager.removeDataProvider(myContainer);
    if (myLogCreationCallback != null) {
      myLogCreationCallback.set(null);
      myLogCreationCallback = null;
    }
    if (myUi != null) {
      MainVcsLogUi ui = myUi;
      myUi = null;
      Disposer.dispose(ui);
    }
  }

  /**
   * Executes a consumer when a main log ui is created. If main log ui already exists, executes it immediately.
   * Overwrites any consumer that was added previously: only the last one gets executed.
   *
   * @param consumer consumer to execute.
   */
  @RequiresEdt
  public void executeOnMainUiCreated(@NotNull Consumer<? super MainVcsLogUi> consumer) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    ListenableFuture<MainVcsLogUi> future = waitMainUiCreation();
    future.addListener(() -> {
      try {
        MainVcsLogUi result = future.get();
        if (result != null) consumer.consume(result);
      }
      catch (InterruptedException | ExecutionException ignore) {
      }
    }, MoreExecutors.directExecutor());
  }

  @RequiresEdt
  public ListenableFuture<MainVcsLogUi> waitMainUiCreation() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    if (myUi == null) {
      if (myLogCreationCallback != null) {
        myLogCreationCallback.set(null);
      }
      myLogCreationCallback = SettableFuture.create();
      return myLogCreationCallback;
    }
    else {
      return Futures.immediateFuture(myUi);
    }
  }

  @Override
  public void disposeContent() {
    disposeMainUi();
  }

  @Nullable
  public static VcsLogContentProvider getInstance(@NotNull Project project) {
    for (ChangesViewContentEP ep : ChangesViewContentEP.EP_NAME.getExtensions(project)) {
      if (ep.getClassName().equals(VcsLogContentProvider.class.getName())) {
        return (VcsLogContentProvider)ep.getCachedInstance();
      }
    }
    return null;
  }

  final static class VcsLogVisibilityPredicate implements Predicate<Project> {
    @Override
    public boolean test(@NotNull Project project) {
      return !VcsProjectLog.getLogProviders(project).isEmpty();
    }
  }

  final static class VcsLogContentPreloader implements ChangesViewContentProvider.Preloader {
    @Override
    public void preloadTabContent(@NotNull Content content) {
      content.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                          ChangesViewContentManager.TabOrderWeight.BRANCHES.getWeight());
    }
  }

  final static class DisplayNameSupplier implements Supplier<String> {
    @Override
    public String get() {
      return VcsLogBundle.message("vcs.log.tab.name");
    }
  }
}
