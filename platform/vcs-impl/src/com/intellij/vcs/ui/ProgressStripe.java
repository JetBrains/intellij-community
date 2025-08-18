// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.progress.ProgressUIUtil;
import com.intellij.util.ui.AnimatedIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ProgressStripe extends JBPanel {
  private final JComponent myTargetComponent;
  private final Disposable myDisposable;
  private final int myStartDelayMs;

  private final @NotNull JBPanel myPanel;
  protected DisposableLoadingDecorator myDecorator;

  public ProgressStripe(@NotNull JComponent targetComponent, @NotNull Disposable parent, int startDelayMs) {
    super(new BorderLayout());
    myPanel = new JBPanel(new BorderLayout());
    myPanel.setOpaque(false);
    myPanel.add(targetComponent);

    myTargetComponent = targetComponent;
    myDisposable = parent;
    myStartDelayMs = startDelayMs;

    createLoadingDecorator();
  }

  public ProgressStripe(@NotNull JComponent targetComponent, @NotNull Disposable parent) {
    this(targetComponent, parent, (int)ProgressUIUtil.DEFAULT_PROGRESS_DELAY_MILLIS);
  }

  @Override
  public void updateUI() {
    super.updateUI();

    // can be null in super constructor.
    // can be disposed during dispose sequence (ThreeComponentsSplitter can trigger updateUI while removing contents of other toolwindows).
    if (myDisposable != null && !Disposer.isDisposed(myDisposable)) {
      createLoadingDecorator();
    }
  }

  private void createLoadingDecorator() {
    if (myDecorator != null) {
      remove(myDecorator.getComponent());
      Disposer.dispose(myDecorator.getDisposable());
    }

    Disposable disposable = Disposer.newDisposable();
    Disposer.register(myDisposable, disposable);
    myDecorator = new DisposableLoadingDecorator(myTargetComponent, myPanel, disposable, myStartDelayMs);

    add(myDecorator.getComponent(), BorderLayout.CENTER);
    myDecorator.setLoadingText("");
  }

  public void startLoading() {
    myDecorator.startLoading(false);
  }

  public void startLoadingImmediately() {
    myDecorator.startLoadingImmediately();
  }

  public void stopLoading() {
    myDecorator.stopLoading();
  }

  public static final class DisposableLoadingDecorator extends LoadingDecorator {
    private final @NotNull Disposable myDisposable;

    DisposableLoadingDecorator(@NotNull JComponent component,
                               @NotNull JPanel contentPanel,
                               @NotNull Disposable disposable,
                               int startDelayMs) {
      super(contentPanel, disposable, startDelayMs, false, ProgressStripeIcon.generateIcon(component));
      myDisposable = disposable;
    }

    public void startLoadingImmediately() {
      doStartLoading(false);
    }

    @Override
    protected @NotNull NonOpaquePanel customizeLoadingLayer(JPanel parent, @NotNull JLabel text, @NotNull AnimatedIcon icon) {
      parent.setLayout(new BorderLayout());

      NonOpaquePanel result = new NonOpaquePanel();
      result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
      result.add(icon);

      parent.add(result, BorderLayout.NORTH);

      return result;
    }

    public @NotNull Disposable getDisposable() {
      return myDisposable;
    }
  }
}
