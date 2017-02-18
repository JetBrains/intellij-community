/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class ProgressStripe extends JBPanel {
  @NotNull
  private final JBPanel myPanel;
  private final NotNullComputable<MyLoadingDecorator> myCreateLoadingDecorator;
  protected MyLoadingDecorator myDecorator;

  public ProgressStripe(@NotNull JComponent targetComponent, @NotNull Disposable parent, int startDelayMs) {
    super(new BorderLayout());
    myPanel = new JBPanel(new BorderLayout());
    myPanel.setOpaque(false);
    myPanel.add(targetComponent);

    myCreateLoadingDecorator = () -> {
      Disposable disposable = Disposer.newDisposable();
      Disposer.register(parent, disposable);
      return new MyLoadingDecorator(targetComponent, myPanel, disposable, startDelayMs);
    };
    createLoadingDecorator();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myCreateLoadingDecorator != null) {
      if (myDecorator != null) {
        remove(myDecorator.getComponent());
        myDecorator.dispose();
      }
      createLoadingDecorator();
    }
  }

  private void createLoadingDecorator() {
    myDecorator = myCreateLoadingDecorator.compute();
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

    public MyLoadingDecorator(@NotNull JComponent component,
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

    public void dispose() {
      Disposer.dispose(myDisposable);
    }
  }
}
