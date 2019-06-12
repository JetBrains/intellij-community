// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.status.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.status.HgChangesetStatus;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class HgIncomingOutgoingWidget extends EditorBasedWidget
  implements StatusBarWidget.IconPresentation, StatusBarWidget.Multiframe, HgUpdater, HgHideableWidget {

  @NotNull private final HgVcs myVcs;
  @NotNull final Project myProject;
  @NotNull private final HgProjectSettings myProjectSettings;
  @NotNull private final HgChangesetStatus myChangesStatus;
  private final boolean myIsIncoming;
  private boolean isAlreadyShown;
  @NotNull private final Icon myEnabledIcon;
  @NotNull private final Icon myDisabledIcon;

  private volatile String myTooltip = "";
  private Icon myCurrentIcon;

  public HgIncomingOutgoingWidget(@NotNull HgVcs vcs,
                                  @NotNull Project project,
                                  @NotNull HgProjectSettings projectSettings,
                                  boolean isIncoming) {
    super(project);
    this.myProject = project;
    this.myIsIncoming = isIncoming;
    myVcs = vcs;
    myProjectSettings = projectSettings;
    myChangesStatus = new HgChangesetStatus(isIncoming ? "In" : "Out");
    isAlreadyShown = false;
    myEnabledIcon = myIsIncoming ? AllIcons.Ide.IncomingChangesOn : AllIcons.Ide.OutgoingChangesOn;
    myDisabledIcon = ObjectUtils.notNull(IconLoader.getDisabledIcon(myEnabledIcon), myEnabledIcon);
    myCurrentIcon = myDisabledIcon;
    Disposer.register(project, this);
  }

  @Override
  public StatusBarWidget copy() {
    return new HgIncomingOutgoingWidget(myVcs, myProject, myProjectSettings, myIsIncoming);
  }

  @NotNull
  @Override
  public String ID() {
    String name = HgIncomingOutgoingWidget.class.getName();
    return myIsIncoming ? "In" + name : "Out" + name;
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    update();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    update();
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    update();
  }

  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Override
  // Updates branch information on click
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> update();
  }


  public boolean isVisible() {
    return myProjectSettings.isCheckIncomingOutgoing();
  }

  @Override
  public void update(final Project project, @Nullable VirtualFile root) {
    if (!isVisible()) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      if ((project == null) || project.isDisposed()) {
        emptyTooltip();
        return;
      }

      boolean changesAvailable = myChangesStatus.getNumChanges() > 0;
      myCurrentIcon = changesAvailable ? myEnabledIcon : myDisabledIcon;
      myTooltip = changesAvailable ? "\n" + myChangesStatus.getToolTip() : "No changes available";
      if (!isVisible() || !isAlreadyShown) return;
      if (myStatusBar != null) myStatusBar.updateWidget(ID());
    });
  }

  @CalledInAwt
  public void activate() {
    MessageBusConnection busConnection = myProject.getMessageBus().connect();
    busConnection.subscribe(HgVcs.STATUS_TOPIC, this);
    busConnection.subscribe(HgVcs.INCOMING_OUTGOING_CHECK_TOPIC, this);

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (null != statusBar && isVisible()) {
      statusBar.addWidget(this, myProject);
      isAlreadyShown = true;
    }
  }

  @CalledInAwt
  public void deactivate() {
    if (!isAlreadyShown) return;
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (null != statusBar) {
      statusBar.removeWidget(ID());
      isAlreadyShown = false;
    }
  }

  @Override
  public void show() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (isAlreadyShown) {
        return;
      }
      StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
      if (null != statusBar && isVisible()) {
        statusBar.addWidget(this, myProject);
        isAlreadyShown = true;
        BackgroundTaskUtil.syncPublisher(myProject, HgVcs.REMOTE_TOPIC).update(myProject, null);
      }
    }, ModalityState.any());
  }

  @Override
  public void hide() {
    ApplicationManager.getApplication().invokeLater(() -> deactivate(), ModalityState.any());
  }

  @Override
  @CalledInAny
  public void update() {
    update(myProject, null);
  }

  private void emptyTooltip() {
    myTooltip = "";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myCurrentIcon;
  }

  public HgChangesetStatus getChangesetStatus() {
    return myChangesStatus;
  }

  //if smb call hide widget then it removed from status bar ans dispose method called.
  // if we do not override dispose IDE call EditorWidget dispose method and set connection to null.
  //next, if we repeat hide/show dispose eth will be callees several times,but connection will be null -> NPE or already disposed message.
  @Override
  public void dispose() {
    if (!isDisposed()) {
      super.dispose();
    }
  }
}

