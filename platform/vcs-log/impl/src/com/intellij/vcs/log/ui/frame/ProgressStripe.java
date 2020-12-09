// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ProgressStripe extends JBPanel {
  private final JComponent myTargetComponent;
  private final Disposable myDisposable;
  private final int myStartDelayMs;

  @NotNull
  private final JBPanel myPanel;
  protected MyLoadingDecorator myDecorator;

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
    myDecorator = new MyLoadingDecorator(myTargetComponent, myPanel, disposable, myStartDelayMs);

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

  private static class MyLoadingDecorator extends LoadingDecorator {
    @NotNull
    private final Disposable myDisposable;

    MyLoadingDecorator(@NotNull JComponent component,
                       @NotNull JPanel contentPanel,
                       @NotNull Disposable disposable,
                       int startDelayMs) {
      super(contentPanel, disposable, startDelayMs, false, ProgressStripeIcon.generateIcon(component));
      myDisposable = disposable;
    }

    public void startLoadingImmediately() {
      _startLoading(false);
    }

    @Override
    protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
      parent.setLayout(new BorderLayout());

      NonOpaquePanel result = new NonOpaquePanel();
      result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
      result.add(icon);

      parent.add(result, BorderLayout.NORTH);

      return result;
    }

    @NotNull
    public Disposable getDisposable() {
      return myDisposable;
    }
  }
}
