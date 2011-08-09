package org.jetbrains.plugins.gradle.remote.api;

import org.jetbrains.annotations.NotNull;

/**
 * Generic interface with common functionality for all remote services that work with gradle tooling api.
 * 
 * @author Denis Zhdanov
 * @since 8/9/11 3:19 PM
 */
public interface RemoteGradleService {

  /**
   * Provides the service with settings to use.
   * 
   * @param settings  settings to use
   */
  void setSettings(@NotNull RemoteGradleProcessSettings settings);
}
