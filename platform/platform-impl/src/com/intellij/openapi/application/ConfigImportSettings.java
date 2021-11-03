// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public interface ConfigImportSettings {
  void importFinished(@NotNull Path newConfigPath);

  /**
   * If there are no configs for previous versions of this product,
   * then configs will be imported from the IDE path selector returned from this method, if they exist.
   *
   * @param programArgs arguments passed to the {@code main} method.
   * @return the {@link PathManager#getPathsSelector() path selector} of an IDE to import configs from,
   * or null if no import should happen if there are no configs for this product.
   */
  @Nullable
  default String getProductToImportFrom(@NotNull List<String> programArgs) {
    return null;
  }

  /**
   * If the vmoptions are modified during the initial config import, normally the IDE should restart to apply these new vmoptions.
   * Returning {@code false} from this method allows to override this behavior and not restart.
   */
  default boolean shouldRestartAfterVmOptionsChange() {
    return true;
  }

  /**
   * @return true if bundled plugins should be imported when importing settings from other product,
   * false otherwise.
   */
  default boolean shouldImportBundledPlugins() {
    return false;
  }

  /**
   * If settings are imported from other product and if bundled plugins importing enabled,
   * then bundled plugin would be imported only if it belongs to one of the categories returned by this method
   *
   * @return the array of plugin categories to import,
   * or null to import all bundled plugins.
   */
  @Nullable
  default Set<@Nullable String> getBundledPluginCategoriesToImport() {
    return null;
  }

  /**
   * If settings are imported from other product and if bundled plugins importing enabled,
   * and plugin category isn't in the set returned from {@code getBundledPluginCategoriesToImport()},
   * it can be whitelisted to be imported anyway
   *
   * @return true if plugin should be imported anyway, false otherwise
   */
  default boolean shouldImportAnyway(PluginId id) {
    return false;
  }
}