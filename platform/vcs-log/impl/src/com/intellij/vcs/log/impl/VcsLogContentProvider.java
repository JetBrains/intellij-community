// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentEP;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.ContentUtilEx;
import com.intellij.util.NotNullFunction;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogContentProvider.class);
  @SuppressWarnings("StaticNonFinalField") //might be changed in other IDEs
  public static String TAB_NAME = "Log";

  @NotNull private final VcsProjectLog myProjectLog;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());
  @Nullable private Consumer<? super VcsLogUiImpl> myOnCreatedListener;

  @Nullable private VcsLogUiImpl myUi;
  @Nullable private Content myContent;

  public VcsLogContentProvider(@NotNull Project project, @NotNull VcsProjectLog projectLog) {
    myProjectLog = projectLog;

    MessageBusConnection connection = project.getMessageBus().connect(projectLog);
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

  @Nullable
  public VcsLogUiImpl getUi() {
    return myUi;
  }

  @Override
  public void initTabContent(@NotNull Content content) {
    myContent = content;
    myContent.setTabName(TAB_NAME);
    updateDisplayName();

    myProjectLog.createLogInBackground(true);

    content.setComponent(myContainer);
    content.setDisposer(() -> {
      disposeContent();

      myContent.setDisplayName(TAB_NAME);
      myContent = null;
    });
  }

  @CalledInAwt
  private void addMainUi(@NotNull VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (myUi == null) {
      myUi = logManager.createLogUi(VcsLogProjectTabsProperties.MAIN_LOG_ID, true, false);
      VcsLogPanel panel = createPanel(logManager, myUi);
      myContainer.add(panel, BorderLayout.CENTER);
      DataManager.registerDataProvider(myContainer, panel);

      updateDisplayName();
      myUi.addFilterListener(this::updateDisplayName);

      if (myOnCreatedListener != null) myOnCreatedListener.consume(myUi);
      myOnCreatedListener = null;
    }
  }

  private void updateDisplayName() {
    if (myContent != null && myUi != null) {
      myContent.setDisplayName(ContentUtilEx.getFullName(TAB_NAME, VcsLogTabsManager.generateDisplayName(myUi)));
    }
  }

  @NotNull
  protected VcsLogPanel createPanel(@NotNull VcsLogManager logManager, @NotNull AbstractVcsLogUi ui) {
    return new VcsLogPanel(logManager, ui);
  }

  @CalledInAwt
  private void disposeMainUi() {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    myContainer.removeAll();
    DataManager.removeDataProvider(myContainer);
    myOnCreatedListener = null;
    if (myUi != null) {
      VcsLogUiImpl ui = myUi;
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
  @CalledInAwt
  public void executeOnMainUiCreated(@NotNull Consumer<? super VcsLogUiImpl> consumer) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());

    if (myUi == null) {
      myOnCreatedListener = consumer;
    }
    else {
      consumer.consume(myUi);
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

  public static class VcsLogVisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(@NotNull Project project) {
      return !VcsProjectLog.getLogProviders(project).isEmpty();
    }
  }
}
