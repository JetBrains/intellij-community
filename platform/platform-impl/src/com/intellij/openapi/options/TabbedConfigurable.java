/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.Disposable;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.options.ex.ConfigurableCardPanel.createConfigurableComponent;

/**
 * @author yole
 */
public abstract class TabbedConfigurable extends CompositeConfigurable<Configurable> implements Configurable.NoScroll,
                                                                                                Configurable.NoMargin {
  protected TabbedPaneWrapper myTabbedPane;
  private final Disposable myParentDisposable;

  protected TabbedConfigurable(@NotNull Disposable parentDisposable) {
    myParentDisposable = parentDisposable;
  }

  @Override
  public JComponent createComponent() {
    myTabbedPane = new TabbedPaneWrapper(myParentDisposable);
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
    myTabbedPane = null;
    super.disposeUIResources();
  }

  public Disposable getParentDisposable() {
    return myParentDisposable;
  }
}
