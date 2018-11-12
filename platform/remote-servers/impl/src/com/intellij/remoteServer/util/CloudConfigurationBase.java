// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.agent.util.CloudAgentConfigBase;
import com.intellij.remoteServer.agent.util.CloudProxySettings;
import com.intellij.remoteServer.configuration.ServerConfigurationBase;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author michael.golubev
 */
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
    final HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
    return new CloudProxySettings() {

      @Override
      public boolean useHttpProxy() {
        return httpConfigurable.USE_HTTP_PROXY;
      }

      @Override
      public String getHost() {
        return httpConfigurable.PROXY_HOST;
      }

      @Override
      public int getPort() {
        return httpConfigurable.PROXY_PORT;
      }

      @Override
      public boolean useAuthentication() {
        return httpConfigurable.PROXY_AUTHENTICATION;
      }

      @Override
      public String getLogin() {
        return httpConfigurable.getProxyLogin();
      }

      @Override
      public String getPassword() {
        return httpConfigurable.getPlainProxyPassword();
      }
    };
  }

  @Transient
  public boolean isPasswordSafe() {
    CredentialAttributes credentialAttributes = createCredentialAttributes();
    return credentialAttributes != null && PasswordSafe.getInstance().get(credentialAttributes) != null;
  }

  @Nullable
  protected CredentialAttributes createCredentialAttributes() {
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
  @Nullable
  protected String getServiceName() {
    return null;
  }

  @Transient
  @Nullable
  protected String getCredentialUser() {
    return getEmail();
  }

  protected static void doSetSafeValue(@Nullable CredentialAttributes credentialAttributes,
                                       @Nullable String credentialUser, @Nullable String secretValue,
                                       @NotNull Consumer<String> unsafeSetter) {

    if (credentialAttributes != null) {
      PasswordSafe.getInstance().set(credentialAttributes, new Credentials(credentialUser, secretValue), false);
      unsafeSetter.accept(null);
    }
    else {
      unsafeSetter.accept(secretValue);
    }
  }

  protected static String doGetSafeValue(@Nullable CredentialAttributes credentialAttributes, @NotNull Supplier<String> unsafeGetter) {
    return Optional.ofNullable(credentialAttributes)
      .map(attr -> PasswordSafe.getInstance().get(credentialAttributes))
      .map(Credentials::getPasswordAsString)
      .orElseGet(unsafeGetter);
  }

  @Nullable
  protected static CredentialAttributes createCredentialAttributes(String serviceName, String credentialsUser) {
    return StringUtil.isEmpty(serviceName) || StringUtil.isEmpty(credentialsUser) ?
           null : new CredentialAttributes(serviceName, credentialsUser);
  }

  @Override
  public void loadState(@NotNull Self state) {
    super.loadState(state);
    migrateToPasswordSafe();
  }

  protected void migrateToPasswordSafe() {
    final String unsafePassword = getPassword();
    if (!StringUtil.isEmpty(unsafePassword)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!StringUtil.isEmpty(unsafePassword)) {
          setPasswordSafe(unsafePassword);
        }
      });
    }
  }
}
