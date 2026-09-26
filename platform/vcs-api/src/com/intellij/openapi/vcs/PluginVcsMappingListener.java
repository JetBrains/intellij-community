// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs;

import java.util.EventListener;

/**
 * Allows receiving notifications about changes in VCS mapping configuration for the project.
 * Use the {@link ProjectLevelVcsManager#VCS_CONFIGURATION_CHANGED_IN_PLUGIN} MessageBus topic to subscribe.
 *
 * @see VcsMappingListener for {@link ProjectLevelVcsManager#VCS_CONFIGURATION_CHANGED} event listener.
 */
public interface PluginVcsMappingListener extends EventListener {
  /**
   * Notifies that the per-directory VCS mapping has changed.
   */
  void directoryMappingChanged();
}