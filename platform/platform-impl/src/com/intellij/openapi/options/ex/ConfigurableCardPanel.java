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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.GradientViewport;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Sergey.Malenkov
 */
public class ConfigurableCardPanel extends CardLayoutPanel<Configurable, Configurable, JComponent> {
  private static final Logger LOG = Logger.getInstance(ConfigurableCardPanel.class);

  private final Map<Configurable, Disposable> myListeners = new ConcurrentHashMap<>();

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

  @Override
  protected JComponent create(Configurable configurable) {
    if (configurable == null) return null;

    return ReadAction.compute(() -> {
      JComponent component = createConfigurableComponent(configurable);
      if (configurable instanceof ConfigurableWrapper && component != null) {
        addEPChangesListener((ConfigurableWrapper)configurable);
      }
      return component;
    });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void addEPChangesListener(@NotNull ConfigurableWrapper wrapper) {
    //for the dynamic configurations we have to update the whole tree
    if (wrapper.getExtensionPoint().dynamic) return;
    
    Configurable.WithEpDependencies configurable = ConfigurableWrapper.cast(Configurable.WithEpDependencies.class, wrapper);
    if (configurable != null && !myListeners.containsKey(wrapper)) {
      Disposable disposable = Disposer.newDisposable();
      Collection<BaseExtensionPointName<?>> dependencies = configurable.getDependencies();
      ExtensionPointListener listener = new ExtensionPointListener() {
        @Override
        public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
          extensionChanged();
        }

        @Override
        public void extensionRemoved(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
          extensionChanged();
        }

        public void extensionChanged() {
          ApplicationManager.getApplication().invokeLater(() -> {
            //dispose resources -> reset nested component
            wrapper.disposeUIResources();
            resetValue(wrapper);
          }, ModalityState.stateForComponent(ConfigurableCardPanel.this), (__) -> ConfigurableCardPanel.this.isDisposed());
        }
      };

      for (BaseExtensionPointName dependency : dependencies) {
        if (dependency instanceof ExtensionPointName) {
          Extensions.getRootArea().getExtensionPoint(dependency.getName()).addExtensionPointListener(listener, false, disposable);
        }
        else if (dependency instanceof ProjectExtensionPointName) {
          Project project = wrapper.getProject();
          assert project != null;
          ((ProjectExtensionPointName)dependency).getPoint(project).addExtensionPointListener(listener, false, disposable);
        }
      }

      myListeners.put(wrapper, disposable);
    }
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
  public static JComponent createConfigurableComponent(Configurable configurable) {
    return configurable == null ? null : ReadAction.compute(() -> {
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
            if (!component.getClass().equals(JPanel.class) && !component.getClass().equals(DialogPanel.class)) {
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
            component = scroll;
          }
        }
      }
      return component;
    });
  }

  @Override
  protected void dispose(Configurable configurable) {
    if (configurable != null) {
      long time = System.currentTimeMillis();
      try {
        configurable.disposeUIResources();
        Disposable disposer = myListeners.remove(configurable);
        if (disposer != null) {
          Disposer.dispose(disposer);
        }
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
        LOG.warn(time + " ms to " + action + " '" + name + "' id=" + id);
      }
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    for (Disposable value : myListeners.values()) {
      Disposer.dispose(value);
    }
    myListeners.clear();
  }
}
