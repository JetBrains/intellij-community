// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * This class provides logic for handling change in EPs and sends the update signals to the listeners.
 * e.g. EPs can be updated when settings dialog is open: in this case we have to update the UI according to the changes.
 */
@ApiStatus.Experimental
class EpBasedConfigurableGroup
  implements Configurable.NoScroll, MutableConfigurableGroup, Weighted, SearchableConfigurable, Disposable {

  @NotNull
  private final AtomicClearableLazyValue<ConfigurableGroup> myValue;
  @Nullable
  private final Project myProject;
  @NotNull
  private final CopyOnWriteArrayList<Listener> myListeners = new CopyOnWriteArrayList<>();

  EpBasedConfigurableGroup(@Nullable Project project,
                           @NotNull Supplier<ConfigurableGroup> delegate) {
    myValue = AtomicClearableLazyValue.create(delegate::get);
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return myValue.getValue().getDisplayName();
  }

  @NotNull
  @Override
  public Configurable[] getConfigurables() {
    return myValue.getValue().getConfigurables();
  }

  @NotNull
  @Override
  public String getId() {
    ConfigurableGroup value = myValue.getValue();
    return value instanceof SearchableConfigurable ? ((SearchableConfigurable)value).getId() : "root";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return null;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public synchronized void addListener(@NotNull Listener listener) {
    if (myListeners.isEmpty()) {
      Project project = myProject;
      if (project == null) project = DefaultProjectFactory.getInstance().getDefaultProject();
      ExtensionPointListener<ConfigurableEP<Configurable>> epListener = createListener();
      Configurable.APPLICATION_CONFIGURABLE.addExtensionPointListener(epListener, this);
      Configurable.PROJECT_CONFIGURABLE.getPoint(project).addExtensionPointListener(epListener, false, this);
    }

    myListeners.add(listener);
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public int getWeight() {
    ConfigurableGroup value = myValue.getValue();
    return value instanceof Weighted ? ((Weighted)value).getWeight() : 0;
  }

  @NotNull
  private ExtensionPointListener<ConfigurableEP<Configurable>> createListener() {
    return new ExtensionPointListener<ConfigurableEP<Configurable>>() {
      @Override
      public void extensionAdded(@NotNull ConfigurableEP<Configurable> extension, @NotNull PluginDescriptor pluginDescriptor) {
        handle();
      }

      @Override
      public void extensionRemoved(@NotNull ConfigurableEP<Configurable> extension, @NotNull PluginDescriptor pluginDescriptor) {
        handle();
      }

      void handle() {
        myValue.drop();
        ApplicationManager.getApplication().invokeLater(() -> {
          for (Listener listener : myListeners) {
            listener.handleUpdate();
          }
        });
      }
    };
  }

  @Override
  public void dispose() {
    myValue.drop();
  }
}
