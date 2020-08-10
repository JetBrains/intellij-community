// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

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
}