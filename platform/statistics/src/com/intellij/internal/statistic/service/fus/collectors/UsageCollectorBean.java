// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UsageCollectorBean implements PluginAware {
  private static final Object NOT_APPLICABLE = new Object();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private PluginDescriptor myPluginDescriptor;

  private volatile Object instance;

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
    FeatureUsagesCollector collector = getCollectorIfApplicable();
    if (collector == null) {
      throw new IllegalStateException("Usage collector is not applicable");
    }
    return collector;
  }

  @Nullable FeatureUsagesCollector getCollectorIfApplicable() {
    Object result = instance;
    if (result == NOT_APPLICABLE) return null;
    if (result != null) return (FeatureUsagesCollector)result;

    synchronized (this) {
      result = instance;
      if (result == NOT_APPLICABLE) return null;
      if (result != null) return (FeatureUsagesCollector)result;

      try {
        //noinspection NonPrivateFieldAccessedInSynchronizedContext
        FeatureUsagesCollector collector = ApplicationManager.getApplication().instantiateClass(implementationClass, myPluginDescriptor);
        instance = collector;
        return collector;
      }
      catch (ExtensionNotApplicableException ignore) {
        instance = NOT_APPLICABLE;
        return null;
      }
    }
  }
}
