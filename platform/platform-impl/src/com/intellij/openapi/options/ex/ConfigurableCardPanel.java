/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.GradientViewport;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.BorderLayout;

/**
 * @author Sergey.Malenkov
 */
public class ConfigurableCardPanel extends CardLayoutPanel<Configurable, Configurable, JComponent> {
  private static final Logger LOG = Logger.getInstance(ConfigurableCardPanel.class);

  @Override
  protected Configurable prepare(Configurable key) {
    long time = System.currentTimeMillis();
    try {
      ConfigurableWrapper.cast(Configurable.class, key); // create wrapped configurable on a pooled thread
    }
    catch (Exception unexpected) {
      LOG.error("cannot prepare configurable", unexpected);
    }
    finally {
      warn(key, "prepare", time);
    }
    return key;
  }

  /**
   * Creates UI component for the specified configurable.
   * If a component is created successfully the configurable will be reset.
   * If the configurable implements {@link MasterDetails},
   * created component will not have the following modifications.
   * If the configurable does not implement {@link Configurable.NoMargin},
   * this method sets an empty border with default margins for created component.
   * If the configurable does not implement {@link Configurable.NoScroll},
   * this method adds a scroll bars for created component.
   */
  @Override
  protected JComponent create(final Configurable configurable) {
    return configurable == null ? null : ApplicationManager.getApplication().runReadAction(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        JComponent component = null;
        long time = System.currentTimeMillis();
        try {
          component = configurable.createComponent();
        }
        catch (Exception unexpected) {
          LOG.error("cannot create configurable component", unexpected);
        }
        finally {
          warn(configurable, "create", time);
        }
        if (component != null) {
          reset(configurable);
          if (ConfigurableWrapper.cast(MasterDetails.class, configurable) == null) {
            if (ConfigurableWrapper.cast(Configurable.NoMargin.class, configurable) == null) {
              if (!component.getClass().equals(JPanel.class)) {
                // some custom components do not support borders
                JPanel panel = new JPanel(new BorderLayout());
                panel.add(BorderLayout.CENTER, component);
                component = panel;
              }
              component.setBorder(JBUI.Borders.empty(5, 10, 10, 10));
            }
            if (ConfigurableWrapper.cast(Configurable.NoScroll.class, configurable) == null) {
              JScrollPane scroll = ScrollPaneFactory.createScrollPane(null, true);
              scroll.setViewport(new GradientViewport(component, JBUI.insetsTop(5), true));
              scroll.getVerticalScrollBar().setUnitIncrement(JBUI.scale(10));
              component = scroll;
            }
          }
        }
        return component;
      }
    });
  }

  @Override
  protected void dispose(Configurable configurable) {
    if (configurable != null) {
      long time = System.currentTimeMillis();
      try {
        configurable.disposeUIResources();
      }
      catch (Exception unexpected) {
        LOG.error("cannot dispose configurable", unexpected);
      }
      finally {
        warn(configurable, "dispose", time);
      }
    }
  }

  public static void reset(Configurable configurable) {
    if (configurable != null) {
      long time = System.currentTimeMillis();
      try {
        configurable.reset();
      }
      catch (Exception unexpected) {
        LOG.error("cannot reset configurable", unexpected);
      }
      finally {
        warn(configurable, "reset", time);
      }
    }
  }

  static void warn(Configurable configurable, String action, long time) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      time = System.currentTimeMillis() - time;
      int threshold = Registry.intValue("ide.settings.configurable.loading.threshold", 0);
      if (0 < threshold && threshold < time) {
        String name = configurable.getDisplayName();
        String id = ConfigurableVisitor.ByID.getID(configurable);
        LOG.warn(String.valueOf(time) + " ms to " + action + " '" + name + "' id=" + id);
      }
    }
  }
}
