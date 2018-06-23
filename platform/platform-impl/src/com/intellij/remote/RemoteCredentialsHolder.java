/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remote;

import com.google.common.collect.ImmutableMap;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author michael.golubev
 */
public class RemoteCredentialsHolder implements MutableRemoteCredentials {
  private static final String SERVICE_NAME_PREFIX = CredentialAttributesKt.SERVICE_NAME_PREFIX + " Remote Credentials ";

  public static final String HOST = "HOST";
  public static final String PORT = "PORT";
  public static final String ANONYMOUS = "ANONYMOUS";
  public static final String USERNAME = "USERNAME";
  public static final String PASSWORD = "PASSWORD";
  public static final String USE_KEY_PAIR = "USE_KEY_PAIR";
  public static final String USE_AUTH_AGENT = "USE_AUTH_AGENT";
  public static final String PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";
  public static final String PASSPHRASE = "PASSPHRASE";

  public static final String SSH_PREFIX = "ssh://";

  private static final Map<AuthType, String> CREDENTIAL_ATTRIBUTES_QUALIFIERS = ImmutableMap.of(AuthType.PASSWORD, "password",
                                                                                                AuthType.KEY_PAIR, "passphrase",
                                                                                                AuthType.OPEN_SSH, "empty");

  private String myHost;
  private int myPort;//will always be equal to myLiteralPort, if it's valid, or equal to 0 otherwise
  private String myLiteralPort;
  @Nullable
  private String myUserName;
  private String myPassword;
  private String myPrivateKeyFile;
  private String myKnownHostsFile;
  private String myPassphrase;
  private boolean myStorePassword;
  private boolean myStorePassphrase;
  @NotNull
  private AuthType myAuthType = AuthType.PASSWORD;

  public static String getCredentialsString(@NotNull RemoteCredentials cred) {
    return SSH_PREFIX + cred.getUserName() + "@" + cred.getHost() + ":" + cred.getLiteralPort();
  }

  @Override
  public String getHost() {
    return myHost;
  }

  public void setHost(String host) {
    myHost = host;
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
  public String getLiteralPort() {
    return myLiteralPort;
  }

  /**
   * Sets string representation of port and its int value, which is equal to string one if it's a valid integer,
   * and is 0 otherwise.
   */
  @Override
  public void setLiteralPort(String portText) {
    myLiteralPort = portText;
    myPort = StringUtil.parseInt(portText, 0);
  }

  @Override
  @Nullable
  @Transient
  public String getUserName() {
    return myUserName;
  }

  public void setUserName(@Nullable String userName) {
    myUserName = userName;
  }

  @Override
  public String getPassword() {
    return myPassword;
  }

  public void setPassword(String password) {
    myPassword = password;
  }

  public void setStorePassword(boolean storePassword) {
    myStorePassword = storePassword;
  }

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
  public String getPrivateKeyFile() {
    return myPrivateKeyFile;
  }

  public void setPrivateKeyFile(String privateKeyFile) {
    myPrivateKeyFile = privateKeyFile;
  }

  @Override
  @Transient
  public String getPassphrase() {
    return myPassphrase;
  }

  public void setPassphrase(String passphrase) {
    myPassphrase = passphrase;
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

  @Deprecated
  @Override
  public boolean isUseKeyPair() {
    return myAuthType == AuthType.KEY_PAIR;
  }

  @Deprecated
  public void setUseKeyPair(boolean useKeyPair) {
    if (useKeyPair) {
      myAuthType = AuthType.KEY_PAIR;
    }
    else {
      if (myAuthType == AuthType.KEY_PAIR) {
        myAuthType = AuthType.PASSWORD;
      }
      else {
        // do nothing
      }
    }
  }

  @NotNull
  public String getSerializedUserName() {
    if (myUserName == null) return "";
    return myUserName;
  }

  private void setSerializedUserName(String userName) {
    if (StringUtil.isEmpty(userName)) {
      myUserName = null;
    }
    else {
      myUserName = userName;
    }
  }

  private void setSerializedPassword(String serializedPassword) {
    if (!StringUtil.isEmpty(serializedPassword)) {
      myPassword = PasswordUtil.decodePassword(serializedPassword);
      myStorePassword = true;
    }
    else {
      myPassword = null;
    }
  }

  private void setSerializedPassphrase(String serializedPassphrase) {
    if (!StringUtil.isEmpty(serializedPassphrase)) {
      myPassphrase = PasswordUtil.decodePassword(serializedPassphrase);
      myStorePassphrase = true;
    }
    else {
      myPassphrase = null;
      myStorePassphrase = false;
    }
  }

  @Deprecated
  @Override
  public boolean isUseAuthAgent() {
    return myAuthType == AuthType.OPEN_SSH;
  }

  @Deprecated
  @Override
  public void setUseAuthAgent(boolean useAuthAgent) {
    if (useAuthAgent) {
      myAuthType = AuthType.OPEN_SSH;
    }
    else {
      if (myAuthType == AuthType.OPEN_SSH) {
        myAuthType = AuthType.PASSWORD;
      }
      else {
        // do nothing
      }
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
    to.setAuthType(from.getAuthType());
    to.setPrivateKeyFile(from.getPrivateKeyFile());
    to.setStorePassword(from.isStorePassword());
    to.setStorePassphrase(from.isStorePassphrase());
  }

  public void load(Element element) {
    setHost(element.getAttributeValue(HOST));
    setLiteralPort(element.getAttributeValue(PORT));
    setSerializedUserName(element.getAttributeValue(USERNAME));
    setSerializedPassword(element.getAttributeValue(PASSWORD));
    setPrivateKeyFile(StringUtil.nullize(element.getAttributeValue(PRIVATE_KEY_FILE)));
    setSerializedPassphrase(element.getAttributeValue(PASSPHRASE));
    boolean useKeyPair = StringUtil.parseBoolean(element.getAttributeValue(USE_KEY_PAIR), false);
    boolean useAuthAgent = StringUtil.parseBoolean(element.getAttributeValue(USE_AUTH_AGENT), false);
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
        setPassword(null);
        setStorePassword(false);
        setPassphrase(null);
        setStorePassphrase(false);
      }
    }

    boolean isAnonymous = StringUtil.parseBoolean(element.getAttributeValue(ANONYMOUS), false);
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

    if (myLiteralPort != null ? !myLiteralPort.equals(holder.myLiteralPort) : holder.myLiteralPort != null) return false;
    if (myStorePassword != holder.myStorePassword) return false;
    if (myStorePassphrase != holder.myStorePassphrase) return false;
    if (myHost != null ? !myHost.equals(holder.myHost) : holder.myHost != null) return false;
    if (myUserName != null ? !myUserName.equals(holder.myUserName) : holder.myUserName != null) return false;
    if (myPassword != null ? !myPassword.equals(holder.myPassword) : holder.myPassword != null) return false;
    if (myPrivateKeyFile != null ? !myPrivateKeyFile.equals(holder.myPrivateKeyFile) : holder.myPrivateKeyFile != null) return false;
    if (myKnownHostsFile != null ? !myKnownHostsFile.equals(holder.myKnownHostsFile) : holder.myKnownHostsFile != null) return false;
    if (myPassphrase != null ? !myPassphrase.equals(holder.myPassphrase) : holder.myPassphrase != null) return false;
    if (myAuthType != holder.myAuthType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myHost != null ? myHost.hashCode() : 0;
    result = 31 * result + (myLiteralPort != null ? myLiteralPort.hashCode() : 0);
    result = 31 * result + (myUserName != null ? myUserName.hashCode() : 0);
    result = 31 * result + (myPassword != null ? myPassword.hashCode() : 0);
    result = 31 * result + (myPrivateKeyFile != null ? myPrivateKeyFile.hashCode() : 0);
    result = 31 * result + (myKnownHostsFile != null ? myKnownHostsFile.hashCode() : 0);
    result = 31 * result + (myPassphrase != null ? myPassphrase.hashCode() : 0);
    result = 31 * result + (myStorePassword ? 1 : 0);
    result = 31 * result + (myStorePassphrase ? 1 : 0);
    result = 31 * result + myAuthType.hashCode();
    return result;
  }
}
