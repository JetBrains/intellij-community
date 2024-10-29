// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.CountComponent;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.WelcomeScreenTab;
import com.intellij.openapi.wm.WelcomeTabFactory;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;

@ApiStatus.Internal
public final class PluginsTabFactory implements WelcomeTabFactory {
  @Override
  public @NotNull WelcomeScreenTab createWelcomeTab(@NotNull Disposable parentDisposable) {
    return new MyDefaultWelcomeScreenTab(parentDisposable);
  }

  private static final class MyDefaultWelcomeScreenTab extends TabbedWelcomeScreen.DefaultWelcomeScreenTab {
    private final PluginUpdatesService myService;
    private final CountComponent myCountLabel = new CountComponent();
    private JComponent myParent;
    private final Disposable parentDisposable;

    private MyDefaultWelcomeScreenTab(@NotNull Disposable parentDisposable) {
      super(IdeBundle.message("welcome.screen.plugins.title"), WelcomeScreenEventCollector.TabType.TabNavPlugins);
      this.parentDisposable = parentDisposable;

      myKeyComponent.setBorder(JBUI.Borders.empty(8, 0, 8, ExperimentalUI.isNewUI() ? 20 : 8));
      myKeyComponent.add(myCountLabel, BorderLayout.EAST);
      myCountLabel.setVisible(false);

      myService = PluginUpdatesService.connectWithCounter(countValue -> {
        @NlsSafe String text = countValue == null || countValue <= 0 ? null : countValue.toString();
        myCountLabel.setText(text);
        myCountLabel.setVisible(text != null);
        if (myParent != null) {
          myParent.repaint();
        }
      });
    }

    @Override
    public @NotNull JComponent getKeyComponent(@NotNull JComponent parent) {
      if (myParent == null) {
        parent.addAncestorListener(new AncestorListenerAdapter() {
          @Override
          public void ancestorRemoved(AncestorEvent event) {
            if (myService != null) {
              myService.dispose();
            }
          }
        });
      }
      myParent = parent;
      return super.getKeyComponent(parent);
    }

    @Override
    protected JComponent buildComponent() {
      PluginManagerConfigurable configurable = new PluginManagerConfigurable();
      Disposer.register(parentDisposable, configurable::disposeUIResources);
      JComponent panel = createPluginsPanel(configurable);
      panel.addAncestorListener(new AncestorListenerAdapter() {
        @Override
        public void ancestorRemoved(AncestorEvent event) {
          if (!configurable.isModified()) {
            return;
          }
          ApplicationManager.getApplication().invokeLater(() -> {
            try {
              configurable.apply();
              WelcomeScreenEventCollector.logPluginsModified();
              InstalledPluginsState.getInstance().runShutdownCallback();
            }
            catch (ConfigurationException exception) {
              Logger.getInstance(PluginsTabFactory.class).error(exception);
            }
          }, ModalityState.nonModal());
        }
      });

      return panel;
    }
  }

  public static @NotNull JComponent createPluginsPanel(PluginManagerConfigurable configurable) {
    BorderLayoutPanel pluginsPanel = JBUI.Panels.simplePanel(configurable.createComponent()).addToTop(configurable.getTopComponent())
      .withBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 0, 0));
    configurable.getTopComponent().setPreferredSize(new JBDimension(configurable.getTopComponent().getPreferredSize().width, 40));
    return pluginsPanel;
  }
}
