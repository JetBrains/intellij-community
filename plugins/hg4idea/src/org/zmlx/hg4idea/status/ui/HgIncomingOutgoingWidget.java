/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.status.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.status.HgChangesetStatus;

import javax.swing.*;
import java.awt.event.MouseEvent;


/**
 * @author Nadya Zabrodina
 */
public class HgIncomingOutgoingWidget extends EditorBasedWidget
  implements StatusBarWidget.IconPresentation, StatusBarWidget.Multiframe, HgUpdater, HgHideableWidget {

  @NotNull private final HgVcs myVcs;
  @NotNull final Project myProject;
  @NotNull private final HgProjectSettings myProjectSettings;
  @NotNull private final HgChangesetStatus myChangesStatus;
  private final boolean myIsIncoming;
  private boolean isAlreadyShown;

  private MessageBusConnection myBusConnection;

  private volatile String myTooltip = "";

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
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
        update();
      }
    };
  }


  public boolean isVisible() {
    return myProjectSettings.isCheckIncomingOutgoing();
  }

  @Override
  public void update(final Project project, @Nullable VirtualFile root) {
    if (!isVisible()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if ((project == null) || project.isDisposed()) {
          emptyTooltip();
          return;
        }

        emptyTooltip();
        if (myChangesStatus.getNumChanges() > 0) {
          myTooltip = "\n" + myChangesStatus.getToolTip();
        }
        myStatusBar.updateWidget(ID());
      }
    });
  }


  public void activate() {
    myBusConnection = myProject.getMessageBus().connect();
    myBusConnection.subscribe(HgVcs.STATUS_TOPIC, this);
    myBusConnection.subscribe(HgVcs.INCOMING_OUTGOING_CHECK_TOPIC, this);

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (null != statusBar && isVisible()) {
      statusBar.addWidget(this, myProject);
      isAlreadyShown = true;
    }
  }

  public void deactivate() {
    if (!isAlreadyShown) return;
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (null != statusBar) {
      statusBar.removeWidget(ID());
      isAlreadyShown = false;
    }
  }

  public void show() {
    if (isAlreadyShown) {
      return;
    }
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (null != statusBar && isVisible()) {
      statusBar.addWidget(this, myProject);
      isAlreadyShown = true;
      myProject.getMessageBus().syncPublisher(HgVcs.REMOTE_TOPIC).update(myProject, null);
    }
  }

  public void hide() {
    deactivate();
  }

  private void update() {
    update(myProject, null);
  }

  private void emptyTooltip() {
    myTooltip = "";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myIsIncoming ? AllIcons.Ide.IncomingChangesOn : AllIcons.Ide.IncomingChangesOff;
  }

  public HgChangesetStatus getChangesetStatus() {
    return myChangesStatus;
  }

  //if smb call hide widget then it removed from status bar ans dispose method called.
  // if we do not override dispose IDE call EditorWidget dispose method and set connection to null.
  //next, if we repeat hide/show dipose eth will be calles several times,but connection will be null -> NPE or already disposed message.
  @Override
  public void dispose() {
    if (!isDisposed()) {
      super.dispose();
    }
  }
}

