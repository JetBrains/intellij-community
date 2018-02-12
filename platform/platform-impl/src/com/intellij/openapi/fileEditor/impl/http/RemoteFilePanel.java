/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingListener;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;

/**
 * @author nik
 */
public class RemoteFilePanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileEditor.impl.http.RemoteFilePanel");
  @NonNls private static final String ERROR_CARD = "error";
  @NonNls private static final String DOWNLOADING_CARD = "downloading";
  @NonNls private static final String EDITOR_CARD = "editor";
  private JPanel myMainPanel;
  private JLabel myProgressLabel;
  private JProgressBar myProgressBar;
  private JButton myCancelButton;
  private JPanel myContentPanel;
  private JLabel myErrorLabel;
  private JButton myTryAgainButton;
  private JButton myChangeProxySettingsButton;
  private JPanel myEditorPanel;
  private JTextField myUrlTextField;
  private JPanel myToolbarPanel;
  private final Project myProject;
  private final HttpVirtualFile myVirtualFile;
  private final MergingUpdateQueue myProgressUpdatesQueue;
  private final MyDownloadingListener myDownloadingListener;
  private final PropertyChangeListener myPropertyChangeListener;
  private @Nullable TextEditor myFileEditor;

  public RemoteFilePanel(final Project project, final HttpVirtualFile virtualFile, @NotNull PropertyChangeListener propertyChangeListener) {
    myProject = project;
    myVirtualFile = virtualFile;
    myPropertyChangeListener = propertyChangeListener;
    myErrorLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
    myUrlTextField.setText(virtualFile.getUrl());
    myProgressUpdatesQueue = new MergingUpdateQueue("downloading progress updates", 300, false, myMainPanel);
    initToolbar(project);

    final RemoteFileInfo remoteFileInfo = virtualFile.getFileInfo();
    myDownloadingListener = new MyDownloadingListener();
    assert remoteFileInfo != null;
    remoteFileInfo.addDownloadingListener(myDownloadingListener);
    myCancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull final ActionEvent e) {
        remoteFileInfo.cancelDownloading();
      }
    });

    myTryAgainButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull final ActionEvent e) {
        showCard(DOWNLOADING_CARD);
        remoteFileInfo.restartDownloading();
      }
    });
    myChangeProxySettingsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        HttpConfigurable.editConfigurable(myMainPanel);
      }
    });

    if (remoteFileInfo.getState() != RemoteFileState.DOWNLOADED) {
      showCard(DOWNLOADING_CARD);
      remoteFileInfo.startDownloading();
    }

    // file could be from cache
    if (remoteFileInfo.getState() == RemoteFileState.DOWNLOADED) {
      switchEditor();
    }
    else {
      String errorMessage = remoteFileInfo.getErrorMessage();
      if (errorMessage != null) {
        myDownloadingListener.errorOccurred(errorMessage);
      }
    }
  }

  private void initToolbar(Project project) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new RefreshRemoteFileAction(myVirtualFile));
    for (RemoteFileEditorActionProvider actionProvider : RemoteFileEditorActionProvider.EP_NAME.getExtensions()) {
      group.addAll(actionProvider.createToolbarActions(project, myVirtualFile));
    }
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("RemoteFilePanel", group, true);
    myToolbarPanel.add(actionToolbar.getComponent(), BorderLayout.CENTER);
  }

  private void showCard(final String name) {
    ((CardLayout)myContentPanel.getLayout()).show(myContentPanel, name);
  }

  private void switchEditor() {
    LOG.debug("Switching editor...");
    AppUIUtil.invokeOnEdt(() -> {
      TextEditor textEditor = (TextEditor)TextEditorProvider.getInstance().createEditor(myProject, myVirtualFile);
      textEditor.addPropertyChangeListener(myPropertyChangeListener);
      myEditorPanel.removeAll();
      myEditorPanel.add(textEditor.getComponent(), BorderLayout.CENTER);
      myFileEditor = textEditor;
      showCard(EDITOR_CARD);
      LOG.debug("Editor for downloaded file opened.");
    }, myProject.getDisposed());
  }

  @Nullable
  public TextEditor getFileEditor() {
    return myFileEditor;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void selectNotify() {
    UIUtil.invokeLaterIfNeeded(() -> {
      myProgressUpdatesQueue.showNotify();
      if (myFileEditor != null) {
        myFileEditor.selectNotify();
      }
    });
  }

  public void deselectNotify() {
    UIUtil.invokeLaterIfNeeded(() -> {
      myProgressUpdatesQueue.hideNotify();
      if (myFileEditor != null) {
        myFileEditor.deselectNotify();
      }
    });
  }

  public void dispose() {
    myVirtualFile.getFileInfo().removeDownloadingListener(myDownloadingListener);
    Disposer.dispose(myProgressUpdatesQueue);
    if (myFileEditor != null) {
      Disposer.dispose(myFileEditor);
    }
  }

  private class MyDownloadingListener implements FileDownloadingListener {
    @Override
    public void fileDownloaded(final VirtualFile localFile) {
      switchEditor();
    }

    @Override
    public void downloadingCancelled() {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myFileEditor != null) {
          showCard(EDITOR_CARD);
        }
        else {
          myErrorLabel.setText("Downloading cancelled");
          showCard(ERROR_CARD);
        }
      });
    }

    @Override
    public void downloadingStarted() {
      ApplicationManager.getApplication().invokeLater(() -> showCard(DOWNLOADING_CARD));
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      ApplicationManager.getApplication().invokeLater(() -> {
        myErrorLabel.setText(errorMessage);
        showCard(ERROR_CARD);
      });
    }

    @Override
    public void progressMessageChanged(final boolean indeterminate, @NotNull final String message) {
      myProgressUpdatesQueue.queue(new Update("progress text") {
        @Override
        public void run() {
          myProgressLabel.setText(message);
        }
      });
    }

    @Override
    public void progressFractionChanged(final double fraction) {
      myProgressUpdatesQueue.queue(new Update("fraction") {
        @Override
        public void run() {
          myProgressBar.setValue((int)Math.round(100 * fraction));
        }
      });
    }
  }
}
