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
package com.intellij.vcs.log.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.UI;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.data.VcsLogProgress;
import com.intellij.vcs.log.ui.frame.DetailsPanel;
import com.intellij.vcs.log.ui.frame.ProgressStripe;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class VcsLogUiUtil {
  @NotNull
  public static JComponent installProgress(@NotNull JComponent component,
                                           @NotNull VcsLogData logData,
                                           @NotNull Disposable disposableParent) {
    ProgressStripe progressStripe =
      new ProgressStripe(component, disposableParent, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
        @Override
        public void updateUI() {
          super.updateUI();
          if (myDecorator != null && logData.getProgress().isRunning()) startLoadingImmediately();
        }
      };
    logData.getProgress().addProgressIndicatorListener(new VcsLogProgress.ProgressListener() {
      @Override
      public void progressStarted() {
        progressStripe.startLoading();
      }

      @Override
      public void progressStopped() {
        progressStripe.stopLoading();
      }
    }, disposableParent);

    return progressStripe;
  }

  @NotNull
  public static JScrollPane setupScrolledGraph(@NotNull VcsLogGraphTable graphTable, int border) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(graphTable, border);
    graphTable.viewportSet(scrollPane.getViewport());
    return scrollPane;
  }

  public static void installDetailsListeners(@NotNull VcsLogGraphTable graphTable,
                                             @NotNull DetailsPanel detailsPanel,
                                             @NotNull VcsLogData logData,
                                             @NotNull Disposable disposableParent) {
    Runnable miniDetailsLoadedListener = () -> {
      graphTable.reLayout();
      graphTable.repaint();
    };
    Runnable containingBranchesListener = () -> {
      detailsPanel.branchesChanged();
      graphTable.repaint(); // we may need to repaint highlighters
    };
    logData.getMiniDetailsGetter().addDetailsLoadedListener(miniDetailsLoadedListener);
    logData.getContainingBranchesGetter().addTaskCompletedListener(containingBranchesListener);

    Disposer.register(disposableParent, () -> {
      logData.getContainingBranchesGetter().removeTaskCompletedListener(containingBranchesListener);
      logData.getMiniDetailsGetter().removeDetailsLoadedListener(miniDetailsLoadedListener);
    });
  }

  @NotNull
  public static SimpleTextAttributes getLinkAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN,
                                    UI.getColor("link.foreground"));
  }
}
