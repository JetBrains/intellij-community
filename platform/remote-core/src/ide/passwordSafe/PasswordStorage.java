// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.credentialStore.CredentialAttributesKt.CredentialAttributes;

public interface PasswordStorage {

  /**
   * @deprecated Please use {@link #set(CredentialAttributes, Credentials)}
   */
  @Deprecated
  default void storePassword(@SuppressWarnings("UnusedParameters") @Nullable Project project,
                             @NotNull Class requestor,
                             @NotNull String key,
                             @Nullable String value) {
    set(CredentialAttributes(requestor, key), value == null ? null : new Credentials(key, value));
  }

  /**
   * @deprecated use {@link #get(CredentialAttributes)} + {@link Credentials#getPasswordAsString()}
   */
  @Deprecated
  @Nullable
  default String getPassword(@SuppressWarnings("UnusedParameters") @Nullable Project project,
                             @NotNull Class requestor,
                             @NotNull String key) {
    Credentials credentials = get(CredentialAttributes(requestor, key));
    return credentials == null ? null : credentials.getPasswordAsString();
  }

  @Nullable
  Credentials get(@NotNull CredentialAttributes attributes);

  void set(@NotNull CredentialAttributes attributes, @Nullable Credentials credentials);
}
