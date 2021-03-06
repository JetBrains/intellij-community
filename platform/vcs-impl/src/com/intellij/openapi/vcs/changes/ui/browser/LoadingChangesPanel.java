// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui.browser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.StringUtilRt.notNullize;

public class LoadingChangesPanel extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(LoadingChangesPanel.class);

  @Nullable private final StatusText myEmptyText;

  @NotNull private final JBLoadingPanel myLoadingPanel;

  @Nullable private ProgressIndicator myIndicator;

  public LoadingChangesPanel(@NotNull JComponent panel, @Nullable StatusText emptyText, @NotNull Disposable disposable) {
    super(new BorderLayout());
    myEmptyText = emptyText;

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), disposable);
    myLoadingPanel.add(panel, BorderLayout.CENTER);
    add(myLoadingPanel, BorderLayout.CENTER);

    Disposer.register(disposable, this);
  }

  @RequiresEdt
  public <T> void loadChangesInBackground(@NotNull ThrowableComputable<? extends T, ? extends VcsException> loadChanges,
                                          @NotNull Consumer<@Nullable T> applyResult) {
    if (myIndicator != null) myIndicator.cancel();
    myIndicator = BackgroundTaskUtil.executeAndTryWait(indicator -> doLoadChanges(loadChanges, applyResult), this::startLoadingProgress);
  }

  protected void startLoadingProgress() {
    myLoadingPanel.startLoading();
    if (myEmptyText != null) myEmptyText.setText("");
  }

  @NotNull
  private <T> Runnable doLoadChanges(@NotNull ThrowableComputable<? extends T, ? extends VcsException> loadChanges,
                                     @NotNull Consumer<@Nullable T> applyResult) {
    try {
      T changes = loadChanges.compute();
      return () -> {
        myLoadingPanel.stopLoading();
        if (myEmptyText != null) myEmptyText.setText(DiffBundle.message("diff.count.differences.status.text", 0));
        applyResult.accept(changes);
      };
    }
    catch (ProcessCanceledException e) {
      return EmptyRunnable.INSTANCE;
    }
    catch (Exception e) {
      LOG.warn(e);
      return () -> {
        myLoadingPanel.stopLoading();
        if (myEmptyText != null) {
          myEmptyText.setText(notNullize(e.getMessage(), VcsBundle.message("changes.cant.load.changes")),
                              SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        applyResult.accept(null);
      };
    }
  }

  @Override
  public void dispose() {
    if (myIndicator != null) myIndicator.cancel();
  }
}
