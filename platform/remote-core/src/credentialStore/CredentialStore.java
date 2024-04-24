// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.credentialStore;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Please see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html">Storing Sensitive Data</a>.
 */
public interface CredentialStore {
  @Nullable
  Credentials get(@NotNull CredentialAttributes attributes);

  @Nullable
  default String getPassword(@NotNull CredentialAttributes attributes) {
    Credentials credentials = get(attributes);
    return credentials == null ? null : credentials.getPasswordAsString();
  }

  void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials);

  default void setPassword(@NotNull CredentialAttributes attributes, @Nullable String password) {
    set(attributes, password == null ? null : new Credentials(attributes.getUserName(), password));
  }
}
