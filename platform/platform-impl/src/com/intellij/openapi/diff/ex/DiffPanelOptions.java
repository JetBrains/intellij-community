/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (myRequestFocusOnNewContent) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(currentSide.getFocusableComponent(), true);
      });
    }
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

  public void showSource(@Nullable Navigatable descriptor) {
    if (descriptor == null || myDiffPanel.getProject() == null || myDiffPanel.getProject().isDefault()) return;
    myShowSourcePolicy.showSource(descriptor, myDiffPanel);
  }

  public interface ShowSourcePolicy {
    void showSource(@NotNull Navigatable descriptor, @NotNull DiffPanelImpl diffPanel);

    ShowSourcePolicy DONT_SHOW = new ShowSourcePolicy() {
      public void showSource(@NotNull Navigatable descriptor, @NotNull DiffPanelImpl diffPanel) {}
    };

    ShowSourcePolicy OPEN_EDITOR = new ShowSourcePolicy() {
      public void showSource(@NotNull Navigatable descriptor, @NotNull DiffPanelImpl diffPanel) {
        descriptor.navigate(true);
      }
    };

    ShowSourcePolicy OPEN_EDITOR_AND_CLOSE_DIFF = new ShowSourcePolicy() {
      public void showSource(@NotNull Navigatable descriptor, @NotNull DiffPanelImpl diffPanel) {
        OPEN_EDITOR.showSource(descriptor, diffPanel);
        if (diffPanel.getOwnerWindow() == null) return;
        Disposer.dispose(diffPanel);

        if (!dialogWrapperClose(diffPanel.getOwnerWindow())) {
          diffPanel.getOwnerWindow().setVisible(false);
          diffPanel.getOwnerWindow().dispose();
        }
      }

      private boolean dialogWrapperClose(Container window) {
        if (!(window instanceof DialogWrapperDialog)) return false;
        while (window instanceof DialogWrapperDialog) {
          DialogWrapperDialog dlg = (DialogWrapperDialog)window;
          window = window.getParent();
          dlg.getDialogWrapper().doCancelAction();
        }
        return true;
      }
    };

    ShowSourcePolicy DEFAULT = new ShowSourcePolicy() {
      public void showSource(@NotNull Navigatable descriptor, @NotNull DiffPanelImpl diffPanel) {
        Window window = diffPanel.getOwnerWindow();
        if (window == null || window instanceof Frame) OPEN_EDITOR.showSource(descriptor, diffPanel);
        else OPEN_EDITOR_AND_CLOSE_DIFF.showSource(descriptor, diffPanel);
      }
    };
  }
}
