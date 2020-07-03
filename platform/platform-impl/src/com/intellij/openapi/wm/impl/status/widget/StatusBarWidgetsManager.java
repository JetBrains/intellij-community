// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status.widget;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.wm.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Service
public final class StatusBarWidgetsManager extends SimpleModificationTracker implements Disposable {
  private static final @NotNull Logger LOG = Logger.getInstance(StatusBar.class);

  private final Map<StatusBarWidgetFactory, StatusBarWidget> myWidgetFactories = new LinkedHashMap<>();
  private final Map<String, StatusBarWidgetFactory> myWidgetIdsMap = new HashMap<>();

  private final Project myProject;

  public StatusBarWidgetsManager(@NotNull Project project) {
    myProject = project;

    StatusBarWidgetFactory.EP_NAME.getPoint().addExtensionPointListener(new ExtensionPointListener<StatusBarWidgetFactory>() {
      @Override
      public void extensionAdded(@NotNull StatusBarWidgetFactory factory, @NotNull PluginDescriptor pluginDescriptor) {
        addWidgetFactory(factory);
      }

      @Override
      public void extensionRemoved(@NotNull StatusBarWidgetFactory factory, @NotNull PluginDescriptor pluginDescriptor) {
        removeWidgetFactory(factory);
      }
    }, true, this);

    //noinspection deprecation
    StatusBarWidgetProvider.EP_NAME.getPoint().addExtensionPointListener(new ExtensionPointListener<StatusBarWidgetProvider>() {
      @Override
      public void extensionAdded(@NotNull StatusBarWidgetProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
        addWidgetFactory(new StatusBarWidgetProviderToFactoryAdapter(myProject, provider));
      }

      @Override
      public void extensionRemoved(@NotNull StatusBarWidgetProvider provider, @NotNull PluginDescriptor pluginDescriptor) {
        removeWidgetFactory(new StatusBarWidgetProviderToFactoryAdapter(myProject, provider));
      }
    }, true, this);
  }

  public void updateWidget(@NotNull Class<? extends StatusBarWidgetFactory> factoryExtension) {
    StatusBarWidgetFactory factory = StatusBarWidgetFactory.EP_NAME.findExtension(factoryExtension);
    synchronized (myWidgetFactories) {
      if (factory == null || !myWidgetFactories.containsKey(factory)) {
        LOG.info("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: " + factoryExtension.getName());
        return;
      }
      updateWidget(factory);
    }
  }

  public void updateWidget(@NotNull StatusBarWidgetFactory factory) {
    if (factory.isAvailable(myProject) &&
        (!factory.isConfigurable() || ServiceManager.getService(StatusBarWidgetSettings.class).isEnabled(factory))) {
      enableWidget(factory);
    }
    else {
      disableWidget(factory);
    }
  }

  public boolean wasWidgetCreated(@Nullable StatusBarWidgetFactory factory) {
    synchronized (myWidgetFactories) {
      return myWidgetFactories.get(factory) != null;
    }
  }

  @Override
  public void dispose() {
    synchronized (myWidgetFactories) {
      myWidgetFactories.forEach((factory, createdWidget) -> disableWidget(factory));
      myWidgetFactories.clear();
    }
  }

  @Nullable
  public StatusBarWidgetFactory findWidgetFactory(@NotNull String widgetId) {
    return myWidgetIdsMap.get(widgetId);
  }

  @NotNull
  public Set<StatusBarWidgetFactory> getWidgetFactories() {
    synchronized (myWidgetFactories) {
      return myWidgetFactories.keySet();
    }
  }

  private void enableWidget(@NotNull StatusBarWidgetFactory factory) {
    List<StatusBarWidgetFactory> availableFactories = StatusBarWidgetFactory.EP_NAME.getExtensionList();
    synchronized (myWidgetFactories) {
      if (!myWidgetFactories.containsKey(factory)) {
        LOG.error("Factory is not registered as `com.intellij.statusBarWidgetFactory` extension: " + factory.getId());
        return;
      }

      StatusBarWidget createdWidget = myWidgetFactories.get(factory);
      if (createdWidget != null) {
        // widget is already enabled
        return;
      }

      StatusBarWidget widget = factory.createWidget(myProject);
      myWidgetFactories.put(factory, widget);
      myWidgetIdsMap.put(widget.ID(), factory);
      String anchor = getAnchor(factory, availableFactories);

      UIUtil.invokeLaterIfNeeded(() -> {
        if (!myProject.isDisposed()) {
          StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
          if (statusBar == null) {
            LOG.error("Cannot add a widget for project without root status bar: " + factory.getId());
            return;
          }
          statusBar.addWidget(widget, anchor, this);
        }
      });
    }
  }

  @NotNull
  private String getAnchor(@NotNull StatusBarWidgetFactory factory, @NotNull List<StatusBarWidgetFactory> availableFactories) {
    if (factory instanceof StatusBarWidgetProviderToFactoryAdapter) {
      return ((StatusBarWidgetProviderToFactoryAdapter)factory).getAnchor();
    }
    int indexOf = availableFactories.indexOf(factory);
    for (int i = indexOf + 1; i < availableFactories.size(); i++) {
      StatusBarWidgetFactory nextFactory = availableFactories.get(i);
      StatusBarWidget widget = myWidgetFactories.get(nextFactory);
      if (widget != null) {
        return StatusBar.Anchors.before(widget.ID());
      }
    }
    for (int i = indexOf - 1; i >= 0; i--) {
      StatusBarWidgetFactory prevFactory = availableFactories.get(i);
      StatusBarWidget widget = myWidgetFactories.get(prevFactory);
      if (widget != null) {
        return StatusBar.Anchors.after(widget.ID());
      }
    }
    return StatusBar.Anchors.DEFAULT_ANCHOR;
  }

  private void disableWidget(@NotNull StatusBarWidgetFactory factory) {
    synchronized (myWidgetFactories) {
      StatusBarWidget createdWidget = myWidgetFactories.put(factory, null);
      if (createdWidget != null) {
        myWidgetIdsMap.remove(createdWidget.ID());
        factory.disposeWidget(createdWidget);
        UIUtil.invokeLaterIfNeeded(() -> {
          if (!myProject.isDisposed()) {
            StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
            if (statusBar != null) {
              statusBar.removeWidget(createdWidget.ID());
            }
          }
        });
      }
    }
  }

  public boolean canBeEnabledOnStatusBar(@NotNull StatusBarWidgetFactory factory, @NotNull StatusBar statusBar) {
    return factory.isAvailable(myProject) && factory.isConfigurable() && factory.canBeEnabledOn(statusBar);
  }

  private void addWidgetFactory(@NotNull StatusBarWidgetFactory factory) {
    synchronized (myWidgetFactories) {
      if (LightEdit.owns(myProject) && !(factory instanceof LightEditCompatible)) {
        return;
      }
      if (myWidgetFactories.containsKey(factory)) {
        LOG.error("Factory has been added already: " + factory.getId());
        return;
      }
      myWidgetFactories.put(factory, null);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          updateWidget(factory);
        }
      });
      incModificationCount();
    }
  }

  private void removeWidgetFactory(@NotNull StatusBarWidgetFactory factory) {
    synchronized (myWidgetFactories) {
      disableWidget(factory);
      myWidgetFactories.remove(factory);
      incModificationCount();
    }
  }
}
