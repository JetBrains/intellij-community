// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class CloudConfigurationUtil {

  public static void doSetSafeValue(@Nullable CredentialAttributes credentialAttributes,
                                    @Nullable String credentialUser,
                                    @Nullable String secretValue) {

    doSetSafeValue(credentialAttributes, credentialUser, secretValue, value -> {});
  }

  public static void doSetSafeValue(@Nullable CredentialAttributes credentialAttributes,
                                    @Nullable String credentialUser,
                                    @Nullable String secretValue,
                                    @NotNull Consumer<? super String> unsafeSetter) {

    if (credentialAttributes != null) {
      PasswordSafe.getInstance().set(credentialAttributes, new Credentials(credentialUser, secretValue), false);
      unsafeSetter.accept(null);
    }
    else {
      unsafeSetter.accept(secretValue);
    }
  }

  public static Optional<String> doGetSafeValue(@Nullable CredentialAttributes credentialAttributes) {
    return Optional.ofNullable(credentialAttributes)
      .map(attributes -> PasswordSafe.getInstance().get(attributes))
      .map(Credentials::getPasswordAsString);
  }

  public static String doGetSafeValue(@Nullable CredentialAttributes credentialAttributes, @NotNull Supplier<String> unsafeGetter) {
    return doGetSafeValue(credentialAttributes).orElseGet(unsafeGetter);
  }

  public static boolean hasSafeCredentials(@Nullable CredentialAttributes credentialAttributes) {
    return credentialAttributes != null && PasswordSafe.getInstance().get(credentialAttributes) != null;
  }

  @Nullable
  public static CredentialAttributes createCredentialAttributes(String serviceName, String credentialsUser) {
    return StringUtil.isEmpty(serviceName) || StringUtil.isEmpty(credentialsUser)
           ? null
           : new CredentialAttributes(serviceName, credentialsUser);
  }
}
