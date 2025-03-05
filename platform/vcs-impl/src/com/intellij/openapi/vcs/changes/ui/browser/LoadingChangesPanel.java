// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui.browser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.commit.FixedSizeScrollPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

import static com.intellij.openapi.util.text.StringUtilRt.notNullize;

public class LoadingChangesPanel extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance(LoadingChangesPanel.class);

  private final @NotNull JBLoadingPanel myLoadingPanel;
  private final @NotNull JScrollPane myErrorPanel;
  private final @NotNull JBLabel myErrorLabel;

  private @Nullable ProgressIndicator myIndicator;

  public LoadingChangesPanel(@NotNull JComponent panel, @Nullable StatusText emptyText, @NotNull Disposable disposable) {
    this(panel, disposable);
  }

  public LoadingChangesPanel(@NotNull JComponent panel, @NotNull Disposable disposable) {
    super(new BorderLayout());

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), disposable);
    myLoadingPanel.add(panel, BorderLayout.CENTER);
    add(myLoadingPanel, BorderLayout.CENTER);

    myErrorLabel = new JBLabel();
    myErrorLabel.setCopyable(true);
    myErrorLabel.setForeground(UIUtil.getErrorForeground());

    myErrorPanel = new FixedSizeScrollPanel(myErrorLabel, new JBDimension(400, 100));
    myErrorPanel.setVisible(false);
    add(myErrorPanel, BorderLayout.NORTH);

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
    myErrorPanel.setVisible(false);
  }

  private @NotNull <T> Runnable doLoadChanges(@NotNull ThrowableComputable<? extends T, ? extends VcsException> loadChanges,
                                              @NotNull Consumer<@Nullable T> applyResult) {
    try {
      T changes = loadChanges.compute();
      return () -> {
        myLoadingPanel.stopLoading();
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
        String text = notNullize(e.getMessage(), VcsBundle.message("changes.cant.load.changes"));
        myErrorLabel.setText(StringUtil.replace(text.trim(), "\n", UIUtil.BR));
        myErrorPanel.setVisible(true);
        applyResult.accept(null);
      };
    }
  }

  @Override
  public void dispose() {
    if (myIndicator != null) myIndicator.cancel();
  }
}
