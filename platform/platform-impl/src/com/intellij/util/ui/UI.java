// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.ui.panel.ProgressPanelBuilder;
import com.intellij.ui.panel.ComponentPanelBuilder;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public class UI extends JBUI {

  public static class PanelFactory {

    /**
     * Creates a panel builder for arbitrary <code>JComponent</code>.
     *
     * @param component is the central component
     * @return a newly created instance of {@link ComponentPanelBuilder} for configuring the panel before
     * creation.
     */
    public static ComponentPanelBuilder panel(JComponent component) {
      return new ComponentPanelBuilder(component);
    }

    /**
     * Creates a panel builder for arbitrary <code>JProgressBar</code>.
     *
     * @param progressBar is the central progressBar
     * @return a newly created instance of {@link ProgressPanelBuilder} for configuring the panel before
     * creation.
     */
    public static ProgressPanelBuilder panel(JProgressBar progressBar) {
      return new ProgressPanelBuilder(progressBar);
    }

    /**
     * Creates a panel grid. Each grid should contain panels of the same type.
     *
     * @return a newly created {@link PanelGridBuilder}
     */
    public static PanelGridBuilder grid() {
      return new PanelGridBuilder();
    }
  }
}
