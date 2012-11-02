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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
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
import org.zmlx.hg4idea.status.HgCurrentBranchStatus;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Widget to display basic hg status in the IJ status bar.
 */
public class HgStatusWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation, StatusBarWidget.Multiframe, HgUpdater {

  private static final String MAX_STRING = "hg: default branch (128)";

  @NotNull private final HgVcs myVcs;
  @NotNull private final HgProjectSettings myProjectSettings;
  @NotNull private final HgCurrentBranchStatus myCurrentBranchStatus;
  private MessageBusConnection myBusConnection;

  private volatile String myText = "";
  private volatile String myTooltip = "";

  public HgStatusWidget(@NotNull HgVcs vcs, @NotNull Project project, @NotNull HgProjectSettings projectSettings) {
    super(project);
    myVcs = vcs;
    myProjectSettings = projectSettings;

    myCurrentBranchStatus = new HgCurrentBranchStatus();
  }

  @Override
  public StatusBarWidget copy() {
    return new HgStatusWidget(myVcs, getProject(), myProjectSettings);
  }

  @NotNull
  @Override
  public String ID() {
    return HgStatusWidget.class.getName();
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    update();
  }

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    update();
  }

  @Override
  public void fileClosed(FileEditorManager source, VirtualFile file) {
    update();
  }

  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @NotNull
  @Override
  public String getText() {
    final String text = myText;
    return StringUtil.isEmpty(text) ? "" : "hg: " + text;
  }

  @NotNull
  @Override
  public String getMaxPossibleText() {
    return MAX_STRING;
  }

  @Override
  public float getAlignment() {
    return Component.LEFT_ALIGNMENT;
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

  @Override
  public void update(final Project project, @Nullable VirtualFile root) {
    update();
  }

  @Override
  public void update(final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if ((project == null) || project.isDisposed()) {
          emptyTextAndTooltip();
          return;
        }

        emptyTextAndTooltip();

        if (null != myCurrentBranchStatus.getStatusText()) {
          myText = myCurrentBranchStatus.getStatusText();
          myTooltip = myCurrentBranchStatus.getToolTipText();
        }

        int maxLength = MAX_STRING.length();
        myText = StringUtil.shortenTextWithEllipsis(myText, maxLength, 5);

        myStatusBar.updateWidget(ID());
      }
    });
  }


  public void activate() {
    Project project = getProject();
    if (null == project) {
      return;
    }

    myBusConnection = project.getMessageBus().connect();
    myBusConnection.subscribe(HgVcs.STATUS_TOPIC, this);

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (null != statusBar) {
      statusBar.addWidget(this, project);
    }
  }

  public void deactivate() {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(getProject());
    if (null != statusBar) {
      statusBar.removeWidget(ID());
    }
  }

  private void update() {
    update(getProject());
  }

  private void emptyTextAndTooltip() {
    myText = "";
    myTooltip = "";
  }

  @NotNull
  public HgCurrentBranchStatus getCurrentBranchStatus() {
    return myCurrentBranchStatus;
  }
}
