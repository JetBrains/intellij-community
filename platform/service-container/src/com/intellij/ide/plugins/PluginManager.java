// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionInstantiationException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

public final class PluginManager {
  public static final String INSTALLED_TXT = "installed.txt";

  /**
   * @return file with list of once installed plugins if it exists, null otherwise
   */
  @Nullable
  public static Path getOnceInstalledIfExists() {
    Path onceInstalledFile = Paths.get(PathManager.getConfigPath(), INSTALLED_TXT);
    return Files.isRegularFile(onceInstalledFile) ? onceInstalledFile : null;
  }

  // not in PluginManagerCore because it is helper method
  @Nullable
  public static IdeaPluginDescriptorImpl loadDescriptor(@NotNull Path file, @NotNull String fileName) {
    return PluginManagerCore.loadDescriptor(file, fileName, PluginManagerCore.disabledPlugins());
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

    if (pluginId == null || PluginManagerCore.CORE_ID == pluginId) {
      if (componentClassName != null) {
        pluginId = PluginManagerCore.getPluginByClassName(componentClassName);
      }
    }
    if (pluginId == null || PluginManagerCore.CORE_ID == pluginId) {
      if (t instanceof ExtensionInstantiationException) {
        pluginId = ((ExtensionInstantiationException)t).getExtensionOwnerId();
      }
    }

    if (pluginId != null && PluginManagerCore.CORE_ID != pluginId) {
      throw new StartupAbortedException("Fatal error initializing plugin " + pluginId.getIdString(), new PluginException(t, pluginId));
    }
    else {
      throw new StartupAbortedException("Fatal error initializing '" + componentClassName + "'", t);
    }
  }

  /**
   * @deprecated Use {@link PluginManagerCore#getPlugin(PluginId)}
   */
  @Nullable
  @Deprecated
  public static IdeaPluginDescriptor getPlugin(@Nullable PluginId id) {
    return PluginManagerCore.getPlugin(id);
  }

  @NotNull
  public static IdeaPluginDescriptor[] getPlugins() {
    return PluginManagerCore.getPlugins();
  }

  public static boolean isPluginInstalled(PluginId id) {
    return PluginManagerCore.isPluginInstalled(id);
  }

  @Nullable
  public static PluginId getPluginByClassName(@NotNull String className) {
    return PluginManagerCore.getPluginByClassName(className);
  }

  @NotNull
  public static List<? extends IdeaPluginDescriptor> getLoadedPlugins() {
    return PluginManagerCore.getLoadedPlugins();
  }

  @Nullable
  public static PluginId getPluginOrPlatformByClassName(@NotNull String className) {
    return PluginManagerCore.getPluginOrPlatformByClassName(className);
  }

  /**
   * @deprecated Bad API, sorry. Please use {@link #isDisabled(PluginId)} to check plugin's state,
   * {@link PluginManagerCore#enablePlugin(PluginId)}/{@link PluginManagerCore#disablePlugin(PluginId)} for state management,
   * {@link PluginManagerCore#disabledPlugins()} to get an unmodifiable collection of all disabled plugins (rarely needed).
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @NotNull
  public static List<String> getDisabledPlugins() {
    return PluginManagerCore.getDisabledPlugins();
  }

  public static void saveDisabledPlugins(@NotNull Collection<String> ids, boolean append) throws IOException {
    PluginManagerCore.saveDisabledPlugins(ContainerUtil.map(ids, s -> PluginId.getId(s)), append);
  }

  public static boolean disablePlugin(@NotNull String id) {
    return PluginManagerCore.disablePlugin(PluginId.getId(id));
  }

  public static boolean enablePlugin(@NotNull String id) {
    return PluginManagerCore.enablePlugin(PluginId.getId(id));
  }

  @NotNull
  public static Logger getLogger() {
    return PluginManagerCore.getLogger();
  }
}