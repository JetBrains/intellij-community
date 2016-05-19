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
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import icons.VcsLogIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class ProgressStripe extends JBLoadingPanel {
  public ProgressStripe(@NotNull JComponent component, @NotNull JComponent toolbar, @NotNull Disposable parent, int startDelayMs) {
    super(new BorderLayout(), panel -> new MyLoadingDecorator(component, toolbar, panel, parent, startDelayMs));
    setLoadingText("");
    add(component);
  }

  private static class MyLoadingDecorator extends LoadingDecorator {
    private Box.Filler myFiller;

    public MyLoadingDecorator(@NotNull JComponent component,
                              @NotNull JComponent toolbar,
                              @NotNull JPanel contentPanel,
                              @NotNull Disposable parent,
                              int startDelayMs) {
      super(contentPanel, parent, startDelayMs, false, StripesAnimatedIcon.generateIcon(component));
      toolbar.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          super.componentResized(e);
          Dimension dimension = new Dimension(0, toolbar.getHeight() - VcsLogIcons.Stripes.getIconHeight() / 2);
          myFiller.changeShape(dimension, dimension, dimension);
        }
      });
    }

    @Override
    protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
      parent.setLayout(new BorderLayout());

      NonOpaquePanel result = new NonOpaquePanel();
      result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
      myFiller = new Box.Filler(new Dimension(), new Dimension(), new Dimension());
      result.add(myFiller);
      result.add(icon);

      parent.add(result, BorderLayout.NORTH);

      return result;
    }
  }
}
