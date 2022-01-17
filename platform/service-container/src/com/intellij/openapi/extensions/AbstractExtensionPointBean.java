// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

/**
 * @deprecated Use {@link com.intellij.serviceContainer.LazyExtensionInstance}.
 */
@Deprecated
public abstract class AbstractExtensionPointBean implements PluginAware {
  protected PluginDescriptor myPluginDescriptor;

  @Transient
  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }

  @Override
  public final void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @Nullable PluginId getPluginId() {
    return myPluginDescriptor == null ? null : myPluginDescriptor.getPluginId();
  }

  public final @NotNull <T> Class<T> findClass(@NotNull String className) throws ClassNotFoundException {
    return findClass(className, myPluginDescriptor);
  }

  public final @NotNull <T> Class<T> findExtensionClass(@NotNull String className) {
    try {
      return findClass(className, myPluginDescriptor);
    }
    catch (Throwable t) {
      throw new PluginException(t, getPluginId());
    }
  }

  private static @NotNull <T> Class<T> findClass(@NotNull String className, @Nullable PluginDescriptor pluginDescriptor) throws ClassNotFoundException {
    ClassLoader classLoader = pluginDescriptor != null ?
                              pluginDescriptor.getClassLoader() :
                              AbstractExtensionPointBean.class.getClassLoader();
    //noinspection unchecked
    return (Class<T>)Class.forName(className, true, classLoader);
  }

  public @NotNull ClassLoader getLoaderForClass() {
    return myPluginDescriptor != null ?
           myPluginDescriptor.getClassLoader() :
           getClass().getClassLoader();
  }

  public final @NotNull <T> T instantiate(@NotNull String className, @NotNull PicoContainer container) throws ClassNotFoundException {
    return ((ComponentManager)container).instantiateClass(className, myPluginDescriptor);
  }
}