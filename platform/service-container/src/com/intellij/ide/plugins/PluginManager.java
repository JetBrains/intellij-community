// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class PluginManager extends PluginManagerCore {
  public static final String INSTALLED_TXT = "installed.txt";

  /**
   * @return file with list of once installed plugins if it exists, null otherwise
   */
  @Nullable
  public static File getOnceInstalledIfExists() {
    File onceInstalledFile = new File(PathManager.getConfigPath(), INSTALLED_TXT);
    return onceInstalledFile.isFile() ? onceInstalledFile : null;
  }

  @SuppressWarnings("unused")
  public static void processException(@NotNull Throwable t) {
    StartupAbortedException.processException(t);
  }

  public static void handleComponentError(@NotNull Throwable t, @Nullable String componentClassName, @Nullable PluginId pluginId) {
    Application app = ApplicationManager.getApplication();
    if (app != null && app.isUnitTestMode()) {
      ExceptionUtil.rethrow(t);
    }

    if (t instanceof StartupAbortedException) {
      throw (StartupAbortedException)t;
    }

    if (pluginId == null || CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      if (componentClassName != null) {
        pluginId = getPluginByClassName(componentClassName);
      }
    }
    if (pluginId == null || CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      if (t instanceof ExtensionInstantiationException) {
        pluginId = ((ExtensionInstantiationException)t).getExtensionOwnerId();
      }
    }

    if (pluginId != null && !CORE_PLUGIN_ID.equals(pluginId.getIdString())) {
      throw new StartupAbortedException("Fatal error initializing plugin " + pluginId.getIdString(), new PluginException(t, pluginId));
    }
    else {
      throw new StartupAbortedException("Fatal error initializing '" + componentClassName + "'", t);
    }
  }
}