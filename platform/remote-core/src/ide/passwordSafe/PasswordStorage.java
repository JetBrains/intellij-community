// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.passwordSafe;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PasswordStorage {
  /** @deprecated Please use {@link #set(CredentialAttributes, Credentials)} */
  @Deprecated
  default void storePassword(@SuppressWarnings("unused") @Nullable Project project, @NotNull Class<?> requestor, @NotNull String key, @Nullable String value) {
    set(new CredentialAttributes(requestor.getName(), key, requestor), value == null ? null : new Credentials(key, value));
  }

  /** @deprecated use {@link #get(CredentialAttributes)} + {@link Credentials#getPasswordAsString()} */
  @Deprecated
  default @Nullable String getPassword(@SuppressWarnings("unused") @Nullable Project project, @NotNull Class<?> requestor, @NotNull String key) {
    var credentials = get(new CredentialAttributes(requestor.getName(), key, requestor));
    return credentials == null ? null : credentials.getPasswordAsString();
  }

  @Nullable Credentials get(@NotNull CredentialAttributes attributes);

  void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials);
}
