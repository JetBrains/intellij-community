// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;

public class PluginsTabFactory implements WelcomeTabFactory {
  private static final Logger LOG = Logger.getInstance(PluginsTabFactory.class);

  @Override
  public @NotNull WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable) {
    return new TabbedWelcomeScreen.DefaultWelcomeScreenTab(IdeBundle.message("welcome.screen.plugins.title")) {
      @Override
      protected JComponent buildComponent() {
        PluginManagerConfigurable configurable = new PluginManagerConfigurable();
        BorderLayoutPanel pluginsPanel = UI.Panels.simplePanel(configurable.createComponent()).addToTop(configurable.getTopComponent())
          .withBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0));
        pluginsPanel.addAncestorListener(new AncestorListenerAdapter() {
          @Override
          public void ancestorRemoved(AncestorEvent event) {
            if (!configurable.isModified()) return;
            try {
              configurable.apply();
            }
            catch (ConfigurationException exception) {
              LOG.error(exception);
            }
          }
        });
        return pluginsPanel;
      }
    };
  }
}
