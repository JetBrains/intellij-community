// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

@ApiStatus.Internal
public interface ConfigImportSettings {
  /**
   * Called after configuration import is finished, even when there was nothing to import from.
   * In the latter case, {@link ConfigImportHelper#isConfigImported()} returns {@code false}.
   */
  void importFinished(@NotNull Path newConfigPath, @Nullable String pathSelectorOfOtherIde);

  /**
   * If there are no configs for previous versions of this product,
   * then configs will be imported from the IDE path selector returned from this method, if they exist.
   *
   * @param programArgs arguments passed to the {@code main} method.
   * @return the {@link PathManager#getPathsSelector() path selector} of an IDE to import configs from,
   * or null if no import should happen if there are no configs for this product.
   */
  default @Nullable String getProductToImportFrom(@NotNull List<String> programArgs) {
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
   * Allows to edit lists of plugins that are about to be migrated or downloaded during import
   */
  default void processPluginsToMigrate(@NotNull Path newConfigDir,
                                       @NotNull Path oldConfigDir,
                                       @NotNull List<IdeaPluginDescriptor> pluginsToMigrate,
                                       @NotNull List<IdeaPluginDescriptor> pluginsToDownload) { }

  /**
   * @param prefix is a platform prefix of {@code configDirectory}
   * @return true if configDirectory should be seen as import candidate while finding configuration directories
   */
  default boolean shouldBeSeenAsImportCandidate(Path configDirectory, @Nullable String prefix, @Nullable String productPrefixOtherIde) {
    return true;
  }

  /**
   * Whether a file should be skipped during configuration import.
   */
  default boolean shouldSkipPath(@NotNull Path path) {
    return false;
  }
}