// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

/**
 * Extension to subscribe to statistics event log and force logging event if recording disabled.
 * Reed limitations in methods java doc.
 * Only plugins listed in {@link PluginInfo#isAllowedToInjectIntoFUS()} could implement this extension point.
 */
@ApiStatus.Internal
public interface ExternalEventLogListenerProviderExtension extends ExternalEventLogListenerProvider {
  ExtensionPointName<ExternalEventLogListenerProviderExtension> EP_NAME = new ExtensionPointName<>("com.intellij.statistic.eventLog.externalListenerProvider");
}
