// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.util;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.agent.util.CloudProxySettings;
import com.intellij.remoteServer.configuration.ServerConfigurationBase;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.net.ProxyCredentialStore;
import com.intellij.util.net.ProxySettings;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class CloudConfigurationBase<Self extends CloudConfigurationBase<Self>>
  extends ServerConfigurationBase<Self> implements CloudAgentConfigBase {

  private String myEmail;
  private String myPassword;

  @Override
  @Attribute("email")
  public String getEmail() {
    return myEmail;
  }

  public void setEmail(String email) {
    myEmail = email;
  }

  @Override
  @Attribute("password")
  public String getPassword() {
    return myPassword;
  }

  public void setPassword(String password) {
    myPassword = password;
  }

  @Transient
  @Override
  public CloudProxySettings getProxySettings() {
    var configuration = ProxySettings.getInstance().getProxyConfiguration();
    var credentials = ProxyCredentialStore.getInstance().getCredentials(configuration);
    return new CloudProxySettings() {
      @Override
      public boolean useHttpProxy() {
        return configuration instanceof ProxyConfiguration.StaticProxyConfiguration;
      }

      @Override
      public String getHost() {
        return configuration instanceof ProxyConfiguration.StaticProxyConfiguration http ? http.getHost() : null;
      }

      @Override
      public int getPort() {
        return configuration instanceof ProxyConfiguration.StaticProxyConfiguration http ? http.getPort() : 0;
      }

      @Override
      public boolean useAuthentication() {
        return credentials != null;
      }

      @Override
      public String getLogin() {
        return credentials == null ? null : credentials.getUserName();
      }

      @Override
      public String getPassword() {
        return credentials == null ? null : credentials.getPasswordAsString();
      }
    };
  }

  @Transient
  public boolean isPasswordSafe() {
    var credentialAttributes = createCredentialAttributes();
    return credentialAttributes != null && PasswordSafe.getInstance().get(credentialAttributes) != null;
  }

  protected @Nullable CredentialAttributes createCredentialAttributes() {
    return createCredentialAttributes(getServiceName(), getCredentialUser());
  }

  @Transient
  public void setPasswordSafe(String password) {
    doSetSafeValue(createCredentialAttributes(), getCredentialUser(), password, this::setPassword);
  }

  @Transient
  @Override
  public String getPasswordSafe() {
    return doGetSafeValue(createCredentialAttributes(), this::getPassword);
  }

  /**
   * Service name for {@link #getPassword()} when stored in the {@link PasswordSafe}
   */
  @Transient
  protected @Nullable String getServiceName() {
    return null;
  }

  @Transient
  protected @Nullable String getCredentialUser() {
    return getEmail();
  }

  protected static void doSetSafeValue(
    @Nullable CredentialAttributes credentialAttributes,
    @Nullable String credentialUser,
    @Nullable String secretValue,
    @NotNull Consumer<String> unsafeSetter
  ) {
    CloudConfigurationUtil.doSetSafeValue(credentialAttributes, credentialUser, secretValue, unsafeSetter);
  }

  protected static String doGetSafeValue(@Nullable CredentialAttributes credentialAttributes, @NotNull Supplier<String> unsafeGetter) {
    return CloudConfigurationUtil.doGetSafeValue(credentialAttributes, unsafeGetter);
  }

  protected static boolean hasSafeCredentials(@Nullable CredentialAttributes credentialAttributes) {
    return CloudConfigurationUtil.hasSafeCredentials(credentialAttributes);
  }

  protected static @Nullable CredentialAttributes createCredentialAttributes(String serviceName, String credentialsUser) {
    return CloudConfigurationUtil.createCredentialAttributes(serviceName, credentialsUser);
  }

  public boolean shouldMigrateToPasswordSafe() {
    return !StringUtil.isEmpty(getPassword());
  }

  public void migrateToPasswordSafe() {
    var unsafePassword = getPassword();
    if (!StringUtil.isEmpty(unsafePassword)) {
      setPasswordSafe(unsafePassword);
    }
  }
}
