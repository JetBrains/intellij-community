// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.google.common.collect.ImmutableMap;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * @author michael.golubev
 */
public class RemoteCredentialsHolder implements MutableRemoteCredentials {
  private static final @NonNls String SERVICE_NAME_PREFIX = CredentialAttributesKt.SERVICE_NAME_PREFIX + " Remote Credentials ";

  @NonNls public static final String HOST = "HOST";
  @NonNls public static final String PORT = "PORT";
  @NonNls public static final String ANONYMOUS = "ANONYMOUS";
  @NonNls public static final String USERNAME = "USERNAME";
  @NonNls public static final String PASSWORD = "PASSWORD";
  @NonNls public static final String USE_KEY_PAIR = "USE_KEY_PAIR";
  @NonNls public static final String USE_AUTH_AGENT = "USE_AUTH_AGENT";
  @NonNls public static final String PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";
  @NonNls public static final String PASSPHRASE = "PASSPHRASE";
  @NonNls public static final String USE_OPENSSH_CONFIG = "USE_OPENSSH_CONFIG";
  @NonNls public static final String CONNECTION_CONFIG_PATCH = "sshConnectionConfigPatch";

  @NonNls public static final String SSH_PREFIX = "ssh://";

  private static final Map<AuthType, @NonNls String> CREDENTIAL_ATTRIBUTES_QUALIFIERS = ImmutableMap.of(AuthType.PASSWORD, "password",
                                                                                                        AuthType.KEY_PAIR, "passphrase",
                                                                                                        AuthType.OPEN_SSH, "empty");

  private @NotNull String myHost = "";
  private int myPort;//will always be equal to myLiteralPort, if it's valid, or equal to 0 otherwise
  private @NotNull String myLiteralPort = "";
  private @Nullable String myUserName;
  private @Nullable String myPassword;
  private @NotNull String myPrivateKeyFile = "";
  private @Nullable String myPassphrase;
  private boolean myStorePassword;
  private boolean myStorePassphrase;
  private boolean myUseOpenSSHConfig;
  private @NotNull AuthType myAuthType = AuthType.PASSWORD;
  private @Nullable SshConnectionConfigPatch myConnectionConfigPatch;

  public RemoteCredentialsHolder() {}

  public RemoteCredentialsHolder(@NotNull RemoteCredentials credentials) {
    copyFrom(credentials);
  }

  public static @NlsSafe String getCredentialsString(@NotNull RemoteCredentials cred) {
    return SSH_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getLiteralPort();
  }

  @Override
  public @NotNull String getHost() {
    return myHost;
  }

  @Override
  public void setHost(@Nullable @NlsSafe String host) {
    myHost = StringUtil.notNullize(host);
  }

  @Override
  public int getPort() {
    return myPort;
  }

  /**
   * Sets both int and String representations of port.
   */
  @Override
  public void setPort(int port) {
    myPort = port;
    myLiteralPort = Integer.toString(port);
  }

  @Override
  public @NotNull String getLiteralPort() {
    return myLiteralPort;
  }

  /**
   * Sets string representation of port and its int value, which is equal to string one if it's a valid integer,
   * and is 0 otherwise.
   */
  @Override
  public void setLiteralPort(@Nullable String portText) {
    myLiteralPort = StringUtil.notNullize(portText);
    myPort = StringUtil.parseInt(portText, 0);
  }

  @Override
  @Nullable
  @Transient
  public String getUserName() {
    return myUserName;
  }

  @Override
  public void setUserName(@Nullable String userName) {
    myUserName = StringUtil.notNullize(userName);
  }

  @Override
  public @Nullable String getPassword() {
    return myPassword;
  }

  @Override
  public void setPassword(@Nullable String password) {
    myPassword = StringUtil.notNullize(password);
  }

  @Override
  public void setStorePassword(boolean storePassword) {
    myStorePassword = storePassword;
  }

  @Override
  public void setStorePassphrase(boolean storePassphrase) {
    myStorePassphrase = storePassphrase;
  }

  @Override
  public boolean isStorePassword() {
    return myStorePassword;
  }

  @Override
  public boolean isStorePassphrase() {
    return myStorePassphrase;
  }

  @Override
  public boolean isOpenSshConfigUsageForced() {
    return myUseOpenSSHConfig;
  }

  @Override
  public void setOpenSshConfigUsageForced(boolean useOpenSSHConfig) {
    myUseOpenSSHConfig = useOpenSSHConfig;
  }

  @Override
  public @NotNull String getPrivateKeyFile() {
    return myPrivateKeyFile;
  }

  @Override
  public void setPrivateKeyFile(@Nullable String privateKeyFile) {
    myPrivateKeyFile = StringUtil.notNullize(privateKeyFile);
  }

  @Override
  @Transient
  public @Nullable String getPassphrase() {
    return myPassphrase;
  }

  @Override
  public void setPassphrase(@Nullable String passphrase) {
    myPassphrase = StringUtil.notNullize(passphrase);
  }

  @NotNull
  @Override
  public AuthType getAuthType() {
    return myAuthType;
  }

  @Override
  public void setAuthType(@NotNull AuthType authType) {
    myAuthType = authType;
  }

  @Nullable
  @Override
  public SshConnectionConfigPatch getConnectionConfigPatch() {
    return myConnectionConfigPatch;
  }

  @Override
  public void setConnectionConfigPatch(@Nullable SshConnectionConfigPatch patch) {
    myConnectionConfigPatch = patch;
  }

  @NotNull
  public String getSerializedUserName() {
    return StringUtil.notNullize(myUserName);
  }

  private void setSerializedUserName(@NonNls String userName) {
    if (StringUtil.isEmpty(userName)) {
      myUserName = null;
    }
    else {
      myUserName = userName;
    }
  }

  private void setSerializedPassword(@NonNls @Nullable String serializedPassword) {
    if (!StringUtil.isEmpty(serializedPassword)) {
      //noinspection deprecation
      myPassword = PasswordUtil.decodePassword(serializedPassword);
      myStorePassword = true;
    }
    else {
      myPassword = null;
    }
  }

  private void setSerializedPassphrase(@Nullable String serializedPassphrase) {
    if (!StringUtil.isEmpty(serializedPassphrase)) {
      //noinspection deprecation
      myPassphrase = PasswordUtil.decodePassword(serializedPassphrase);
      myStorePassphrase = true;
    }
    else {
      myPassphrase = "";
      myStorePassphrase = false;
    }
  }

  public void copyRemoteCredentialsTo(@NotNull MutableRemoteCredentials to) {
    copyRemoteCredentials(this, to);
  }

  public void copyFrom(@NotNull RemoteCredentials from) {
    copyRemoteCredentials(from, this);
  }

  public static void copyRemoteCredentials(@NotNull RemoteCredentials from, @NotNull MutableRemoteCredentials to) {
    to.setHost(from.getHost());
    to.setLiteralPort(from.getLiteralPort());//then port is copied
    to.setUserName(from.getUserName());
    to.setPassword(from.getPassword());
    to.setPassphrase(from.getPassphrase());
    to.setAuthType(from.getAuthType());
    to.setPrivateKeyFile(from.getPrivateKeyFile());
    to.setStorePassword(from.isStorePassword());
    to.setStorePassphrase(from.isStorePassphrase());
    to.setOpenSshConfigUsageForced(from.isOpenSshConfigUsageForced());
    to.setConnectionConfigPatch(from.getConnectionConfigPatch());
  }

  public void load(Element element) {
    setHost(element.getAttributeValue(HOST));
    setLiteralPort(element.getAttributeValue(PORT));
    setSerializedUserName(element.getAttributeValue(USERNAME));
    setSerializedPassword(element.getAttributeValue(PASSWORD));
    setPrivateKeyFile(StringUtil.nullize(element.getAttributeValue(PRIVATE_KEY_FILE)));
    setSerializedPassphrase(element.getAttributeValue(PASSPHRASE));
    // true by default for all IDEs except DataGrip due to historical reasons
    setOpenSshConfigUsageForced(Boolean.parseBoolean(StringUtil.defaultIfEmpty(element.getAttributeValue(USE_OPENSSH_CONFIG),
                                                                               String.valueOf(!PlatformUtils.isDataGrip()))));
    boolean useKeyPair = Boolean.parseBoolean(element.getAttributeValue(USE_KEY_PAIR));
    boolean useAuthAgent = Boolean.parseBoolean(element.getAttributeValue(USE_AUTH_AGENT));
    if (useKeyPair) {
      myAuthType = AuthType.KEY_PAIR;
    }
    else if (useAuthAgent) {
      // the old `USE_AUTH_AGENT` attribute is used to avoid settings migration
      myAuthType = AuthType.OPEN_SSH;
    }
    else {
      myAuthType = AuthType.PASSWORD;
    }
    // try to load credentials from PasswordSafe
    final CredentialAttributes attributes = createAttributes(false);
    final Credentials credentials = PasswordSafe.getInstance().get(attributes);
    if (credentials != null) {
      final boolean memoryOnly = PasswordSafe.getInstance().isPasswordStoredOnlyInMemory(attributes, credentials);
      if (myAuthType == AuthType.KEY_PAIR) {
        setPassword(null);
        setStorePassword(false);
        setPassphrase(credentials.getPasswordAsString());
        setStorePassphrase(!memoryOnly);
      }
      else if (myAuthType == AuthType.PASSWORD) {
        setPassword(credentials.getPasswordAsString());
        setStorePassword(!memoryOnly);
        setPassphrase(null);
        setStorePassphrase(false);
      }
      else {
        setOpenSshConfigUsageForced(true);
        setPassword(null);
        setStorePassword(false);
        setPassphrase(null);
        setStorePassphrase(false);
      }
    }

    boolean isAnonymous = Boolean.parseBoolean(element.getAttributeValue(ANONYMOUS));
    if (isAnonymous) {
      setSerializedUserName("anonymous");
      setSerializedPassword("user@example.com");
    }
  }

  /**
   * Stores main part of ssh credentials in xml element and password and passphrase in PasswordSafe.
   * <p>
   * Don't use this method to serialize intermediate state of credentials
   * because it will overwrite password and passphrase in PasswordSafe
   */
  public void save(Element rootElement) {
    rootElement.setAttribute(HOST, StringUtil.notNullize(getHost()));
    rootElement.setAttribute(PORT, StringUtil.notNullize(getLiteralPort()));
    rootElement.setAttribute(USERNAME, getSerializedUserName());
    rootElement.setAttribute(PRIVATE_KEY_FILE, StringUtil.notNullize(getPrivateKeyFile()));
    rootElement.setAttribute(USE_KEY_PAIR, Boolean.toString(myAuthType == AuthType.KEY_PAIR));
    rootElement.setAttribute(USE_OPENSSH_CONFIG, Boolean.toString(isOpenSshConfigUsageForced()));
    // the old `USE_AUTH_AGENT` attribute is used to avoid settings migration
    rootElement.setAttribute(USE_AUTH_AGENT, Boolean.toString(myAuthType == AuthType.OPEN_SSH));

    boolean memoryOnly = (myAuthType == AuthType.KEY_PAIR && !isStorePassphrase())
                         || (myAuthType == AuthType.PASSWORD && !isStorePassword())
                         || myAuthType == AuthType.OPEN_SSH;
    String password;
    if (myAuthType == AuthType.KEY_PAIR) {
      password = getPassphrase();
    }
    else if (myAuthType == AuthType.PASSWORD) {
      password = getPassword();
    }
    else {
      password = null;
    }
    PasswordSafe.getInstance().set(createAttributes(memoryOnly), new Credentials(getUserName(), password));

    // getConnectionConfigPatch() is omitted intentionally.
    // It's expected that the options will be set with SSH settings by one of `copyTo` calls.
  }

  @NotNull
  private CredentialAttributes createAttributes(boolean memoryOnly) {
    final String serviceName =
      SERVICE_NAME_PREFIX + getCredentialsString(this) + "(" + CREDENTIAL_ATTRIBUTES_QUALIFIERS.get(myAuthType) + ")";
    return new CredentialAttributes(serviceName, getUserName(), null, memoryOnly);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteCredentialsHolder holder = (RemoteCredentialsHolder)o;

    if (!myLiteralPort.equals(holder.myLiteralPort)) return false;
    if (myStorePassword != holder.myStorePassword) return false;
    if (myStorePassphrase != holder.myStorePassphrase) return false;
    if (!myHost.equals(holder.myHost)) return false;
    if (!Objects.equals(myUserName, holder.myUserName)) return false;
    if (!Objects.equals(myPassword, holder.myPassword)) return false;
    if (!myPrivateKeyFile.equals(holder.myPrivateKeyFile)) return false;
    if (!Objects.equals(myPassphrase, holder.myPassphrase)) return false;
    if (myUseOpenSSHConfig != holder.myUseOpenSSHConfig) return false;
    if (myAuthType != holder.myAuthType) return false;
    if (!Objects.equals(myConnectionConfigPatch, holder.myConnectionConfigPatch)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myHost.hashCode();
    result = 31 * result + myLiteralPort.hashCode();
    result = 31 * result + (myUserName != null ? myUserName.hashCode() : 0);
    result = 31 * result + (myPassword != null ? myPassword.hashCode() : 0);
    result = 31 * result + myPrivateKeyFile.hashCode();
    result = 31 * result + (myPassphrase != null ? myPassphrase.hashCode() : 0);
    result = 31 * result + (myStorePassword ? 1 : 0);
    result = 31 * result + (myStorePassphrase ? 1 : 0);
    result = 31 * result + (myUseOpenSSHConfig ? 1 : 0);
    result = 31 * result + myAuthType.hashCode();
    result = 31 * result + (myConnectionConfigPatch != null ? myConnectionConfigPatch.hashCode() : 0);
    return result;
  }
}
