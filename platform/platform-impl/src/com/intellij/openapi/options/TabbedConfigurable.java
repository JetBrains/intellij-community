// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ui.JBUI;

import javax.swing.*;

import static com.intellij.openapi.options.ex.ConfigurableCardPanel.createConfigurableComponent;


public abstract class TabbedConfigurable extends CompositeConfigurable<Configurable> implements Configurable.NoScroll,
                                                                                                Configurable.NoMargin {
  protected TabbedPaneWrapper myTabbedPane;
  private final Disposable myDisposable = Disposer.newDisposable();

  @Override
  public JComponent createComponent() {
    myTabbedPane = new TabbedPaneWrapper(myDisposable);
    createConfigurableTabs();
    final JComponent component = myTabbedPane.getComponent();
    component.setBorder(JBUI.Borders.emptyTop(5));
    component.setPreferredSize(JBUI.size(500, 400));
    return component;
  }

  protected void createConfigurableTabs() {
    for (Configurable configurable : getConfigurables()) {
      myTabbedPane.addTab(configurable.getDisplayName(), createConfigurableComponent(configurable));
    }
  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    Disposer.dispose(myDisposable);
    myTabbedPane = null;
  }
}
