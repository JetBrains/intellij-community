// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.panel;

import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.ui.panel.JBPanelFactory;
import com.intellij.openapi.ui.panel.PanelGridBuilder;
import com.intellij.openapi.ui.panel.ProgressPanelBuilder;

import javax.swing.*;

public class PanelFactoryImpl extends JBPanelFactory {
  @Override
  public ComponentPanelBuilder createComponentPanelBuilder(JComponent component) {
    return new ComponentPanelBuilderImpl(component);
  }

  @Override
  public ProgressPanelBuilder createProgressPanelBuilder(JProgressBar progressBar) {
    return new ProgressPanelBuilderImpl(progressBar);
  }

  @Override
  public PanelGridBuilder createPanelGridBuilder() {
    return new PanelGridBuilderImpl();
  }
}
