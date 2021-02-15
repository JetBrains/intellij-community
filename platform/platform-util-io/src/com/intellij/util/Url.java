// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// We don't use Java URI due to problem - http://cns-etuat-2.localnet.englishtown.com/school/e12/#school/45383/201/221/382?c=countrycode=cc|culturecode=en-us|partnercode=mkge
// it is illegal URI (fragment before query), but we must support such URI
// Semicolon as parameters separator is supported (WEB-6671)
public interface Url {
  /**
   * System-independent path
   */
  @NotNull String getPath();

  @Contract(pure = true)
  boolean isInLocalFileSystem();

  @NlsSafe String toDecodedForm();

  @NotNull String toExternalForm();

  @Nullable String getScheme();

  @Nullable String getAuthority();

  @Nullable String getParameters();

  boolean equalsIgnoreParameters(@Nullable Url url);

  boolean equalsIgnoreCase(@Nullable Url url);

  @NotNull Url trimParameters();

  int hashCodeCaseInsensitive();

  @NotNull Url resolve(@NotNull String subPath);

  /**
   * Creates a new url with added parameters.
   */
  @Contract(pure = true)
  @NotNull Url addParameters(@NotNull Map<String, String> parameters);
}
