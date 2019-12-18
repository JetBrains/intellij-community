// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * This class provides logic for handling change in EPs and sends the update signals to the listeners.
 * e.g. EPs can be updated when settings dialog is open: in this case we have to update the UI according to the changes.
 */
@ApiStatus.Experimental
class EpBasedConfigurableGroup
  implements Configurable.NoScroll, MutableConfigurableGroup, Weighted, SearchableConfigurable, Disposable {

  @Nullable
  private final Project myProject;

  @NotNull
  private final AtomicClearableLazyValue<ConfigurableGroup> myValue;
  @NotNull
  private final CopyOnWriteArrayList<Listener> myListeners = new CopyOnWriteArrayList<>();
  @NotNull
  private final List<ConfigurableWrapper> myExtendableEp;

  EpBasedConfigurableGroup(@Nullable Project project,
                           @NotNull Supplier<ConfigurableGroup> delegate) {
    myValue = AtomicClearableLazyValue.create(delegate::get);
    myProject = project;

    List<Configurable> all = new ConfigurableVisitor() {

      @Override
      protected boolean accept(@NotNull Configurable configurable) {
        if (!(configurable instanceof ConfigurableWrapper)) return false;
        ConfigurableEP<?> ep = ((ConfigurableWrapper)configurable).getExtensionPoint();
        return (ep.childrenEPName != null || ep.dynamic);
      }
    }.findAll(Collections.singletonList(myValue.getValue()));

    myExtendableEp = StreamEx.of(all).select(ConfigurableWrapper.class).toImmutableList();
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public synchronized void addListener(@NotNull Listener listener) {
    if (myListeners.isEmpty()) {
      Project project = myProject;
      if (project == null) project = DefaultProjectFactory.getInstance().getDefaultProject();
      ExtensionPointListener epListener = createListener();
      Configurable.APPLICATION_CONFIGURABLE.addExtensionPointListener(epListener, this);
      Configurable.PROJECT_CONFIGURABLE.getPoint(project).addExtensionPointListener(epListener, false, this);

      for (ConfigurableWrapper wrapper : myExtendableEp) {
        ConfigurableEP<?> ep = wrapper.getExtensionPoint();
        Project areaProject = wrapper.getProject();
        ExtensionsArea area = areaProject == null ? ApplicationManager.getApplication().getExtensionArea() : areaProject.getExtensionArea();
        if (ep.childrenEPName != null) {
          ExtensionPoint point = area.getExtensionPointIfRegistered(ep.childrenEPName);
          if (point != null) {
            point.addExtensionPointListener(epListener, false, this);
          }
        }
        else if (ep.dynamic) {
          WithEpDependencies cast = ConfigurableWrapper.cast(WithEpDependencies.class, wrapper);
          if (cast != null) {
            Collection<BaseExtensionPointName<?>> dependencies = cast.getDependencies();
            dependencies.forEach(el -> area.getExtensionPoint(el.getName()).addExtensionPointListener(epListener, false, this));
          }
        }
      }
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
  private ExtensionPointListener<?> createListener() {
    return new ExtensionPointListener<Object>() {

      @Override
      public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
        handle();
      }

      @Override
      public void extensionRemoved(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
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
    myListeners.clear();
  }
}
