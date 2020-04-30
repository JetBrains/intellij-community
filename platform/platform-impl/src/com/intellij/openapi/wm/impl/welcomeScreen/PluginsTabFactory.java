// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PluginsTabFactory implements WelcomeTabFactory {
  @Override
  public @NotNull WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable) {
    return new TabbedWelcomeScreen.DefaultWelcomeScreenTab("Plugins") {
      @Override
      protected JComponent buildComponent() {
        PluginManagerConfigurable configurable = new PluginManagerConfigurable();
        return UI.Panels.simplePanel(configurable.createComponent()).addToTop(configurable.getTopComponent())
          .withBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0));
      }
    };
  }
}
