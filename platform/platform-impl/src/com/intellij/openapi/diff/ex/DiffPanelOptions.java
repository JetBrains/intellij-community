/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.DiffSideView;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;

import java.awt.*;

/**
 * @author dyoma
 */
public class DiffPanelOptions {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.ex.DiffPanelOptions");
  private final DiffPanelImpl myDiffPanel;
  private boolean myRequestFocusOnNewContent = true;
  private ShowSourcePolicy myShowSourcePolicy = ShowSourcePolicy.DEFAULT;

  public DiffPanelOptions(DiffPanelImpl diffPanel) {
    myDiffPanel = diffPanel;
  }

  public void onNewContent(DiffSideView currentSide) {
    if (myRequestFocusOnNewContent)
      currentSide.getFocusableComponent().requestFocus();
  }

  public void setRequestFocusOnNewContent(boolean requestFocusOnNewContent) {
    myRequestFocusOnNewContent = requestFocusOnNewContent;
  }

  public void setShowSourcePolicy(ShowSourcePolicy showSourcePolicy) {
    if (showSourcePolicy == null) {
      LOG.error("");
      return;
    }
    myShowSourcePolicy = showSourcePolicy;
  }

  public void showSource(OpenFileDescriptor descriptor) {
    myShowSourcePolicy.showSource(descriptor, myDiffPanel);
  }

  public static interface ShowSourcePolicy {
    void showSource(OpenFileDescriptor descriptor, DiffPanelImpl diffPanel);

    ShowSourcePolicy DONT_SHOW = new ShowSourcePolicy() {
      public void showSource(OpenFileDescriptor descriptor, DiffPanelImpl diffPanel) {}
    };

    ShowSourcePolicy OPEN_EDITOR = new ShowSourcePolicy() {
      public void showSource(OpenFileDescriptor descriptor, DiffPanelImpl diffPanel) {
        FileEditorManager.getInstance(diffPanel.getProject()).openTextEditor(descriptor, true);
      }
    };

    ShowSourcePolicy OPEN_EDITOR_AND_CLOSE_DIFF = new ShowSourcePolicy() {
      public void showSource(OpenFileDescriptor descriptor, DiffPanelImpl diffPanel) {
        OPEN_EDITOR.showSource(descriptor, diffPanel);
        if (diffPanel.getOwnerWindow() == null) return;
        diffPanel.dispose();

        if (!dialogWrapperClose(diffPanel.getOwnerWindow())) {
          diffPanel.getOwnerWindow().setVisible(false);
          diffPanel.getOwnerWindow().dispose();
        }
      }

      private boolean dialogWrapperClose(Window window) {
        if (!(window instanceof DialogWrapperDialog)) return false;
        DialogWrapperDialog dlg = (DialogWrapperDialog)window;
        dlg.getDialogWrapper().close(DialogWrapper.OK_EXIT_CODE);
        return true;
      }
    };

    ShowSourcePolicy DEFAULT = new ShowSourcePolicy() {
      public void showSource(OpenFileDescriptor descriptor, DiffPanelImpl diffPanel) {
        Window window = diffPanel.getOwnerWindow();
        if (window == null) return;
        else if (window instanceof Frame) OPEN_EDITOR.showSource(descriptor, diffPanel);
        else OPEN_EDITOR_AND_CLOSE_DIFF.showSource(descriptor, diffPanel);
      }
    };
  }
}
