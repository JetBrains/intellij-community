// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class UsageCollectorBean implements PluginAware {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private PluginDescriptor myPluginDescriptor;

  private volatile FeatureUsagesCollector instance;

  @Attribute("implementation")
  @RequiredElement
  public String implementationClass;

  /**
   * Marker that allows earlier execution of application usage collector.
   * <br/>
   * <b>Do not</b> use without approval from Product Analytics Platform Team.
   */
  @Attribute("allowOnStartup") public @NonNls Boolean allowOnStartup;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public @NotNull FeatureUsagesCollector getCollector() {
    if (instance != null) return instance;

    synchronized (this) {
      if (instance != null) return instance;

      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      instance = ApplicationManager.getApplication().instantiateClass(implementationClass, myPluginDescriptor);
    }

    return instance;
  }
}
