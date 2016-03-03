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

import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author michael.golubev
 */
public class RemoteCredentialsHolder implements MutableRemoteCredentials {

  public static final String HOST = "HOST";
  public static final String PORT = "PORT";
  public static final String ANONYMOUS = "ANONYMOUS";
  public static final String USERNAME = "USERNAME";
  public static final String PASSWORD = "PASSWORD";
  public static final String USE_KEY_PAIR = "USE_KEY_PAIR";
  public static final String PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";
  public static final String KNOWN_HOSTS_FILE = "MY_KNOWN_HOSTS_FILE";
  public static final String PASSPHRASE = "PASSPHRASE";
  
  public static final String SSH_PREFIX = "ssh://";

  private String myHost;
  private int myPort;//will always be equal to myLiteralPort, if it's valid, or equal to 0 otherwise
  private String myLiteralPort;
  private boolean myAnonymous;
  private String myUserName;
  private String myPassword;
  private boolean myUseKeyPair;
  private String myPrivateKeyFile;
  private String myKnownHostsFile;
  private String myPassphrase;
  private boolean myStorePassword;
  private boolean myStorePassphrase;

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
  public String getLiteralPort(){
    return myLiteralPort;
  }

  /**
   * Sets string representation of port and its int value, which is equal to string one if it's a valid integer,
   * and is 0 otherwise.
   */
  @Override
  public void setLiteralPort(String portText){
    myLiteralPort = portText;
    myPort = StringUtil.parseInt(portText, 0);
  }

  @Override
  @Transient
  public String getUserName() {
    return myUserName;
  }

  public void setUserName(String userName) {
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
  public boolean isAnonymous() {
    return myAnonymous;
  }

  public void setAnonymous(boolean anonymous) {
    myAnonymous = anonymous;
  }

  @Override
  public String getPrivateKeyFile() {
    return myPrivateKeyFile;
  }

  public void setPrivateKeyFile(String privateKeyFile) {
    myPrivateKeyFile = privateKeyFile;
  }

  @Override
  public String getKnownHostsFile() {
    return myKnownHostsFile;
  }

  public void setKnownHostsFile(String knownHostsFile) {
    myKnownHostsFile = knownHostsFile;
  }

  @Override
  @Transient
  public String getPassphrase() {
    return myPassphrase;
  }

  public void setPassphrase(String passphrase) {
    myPassphrase = passphrase;
  }

  @Override
  public boolean isUseKeyPair() {
    return myUseKeyPair;
  }

  public void setUseKeyPair(boolean useKeyPair) {
    myUseKeyPair = useKeyPair;
  }

  public String getSerializedUserName() {
    if (myAnonymous || myUserName == null) return "";
    return myUserName;
  }

  public void setSerializedUserName(String userName) {
    if (StringUtil.isEmpty(userName)) {
      myUserName = null;
    }
    else {
      myUserName = userName;
    }
  }

  @NotNull
  public String getSerializedPassword() {
    if (myAnonymous) return "";

    if (myStorePassword) {
      return PasswordUtil.encodePassword(myPassword);
    }
    else {
      return "";
    }
  }

  public void setSerializedPassword(String serializedPassword) {
    if (!StringUtil.isEmpty(serializedPassword)) {
      myPassword = PasswordUtil.decodePassword(serializedPassword);
      myStorePassword = true;
    }
    else {
      myPassword = null;
    }
  }

  @NotNull
  public String getSerializedPassphrase() {
    if (myStorePassphrase) {
      return PasswordUtil.encodePassword(myPassphrase);
    }
    else {
      return "";
    }
  }

  public void setSerializedPassphrase(String serializedPassphrase) {
    if (!StringUtil.isEmpty(serializedPassphrase)) {
      myPassphrase = PasswordUtil.decodePassword(serializedPassphrase);
      myStorePassphrase = true;
    }
    else {
      myPassphrase = null;
      myStorePassphrase = false;
    }
  }

  public void copyRemoteCredentialsTo(@NotNull MutableRemoteCredentials to) {
    copyRemoteCredentials(this, to);
  }

  public void copyFrom(RemoteCredentials from) {
    copyRemoteCredentials(from, this);
  }

  public static void copyRemoteCredentials(@NotNull RemoteCredentials from, @NotNull MutableRemoteCredentials to) {
    to.setHost(from.getHost());
    to.setLiteralPort(from.getLiteralPort());//then port is copied
    to.setAnonymous(from.isAnonymous());
    to.setUserName(from.getUserName());
    to.setPassword(from.getPassword());
    to.setUseKeyPair(from.isUseKeyPair());
    to.setPrivateKeyFile(from.getPrivateKeyFile());
    to.setKnownHostsFile(from.getKnownHostsFile());
    to.setStorePassword(from.isStorePassword());
    to.setStorePassphrase(from.isStorePassphrase());
  }

  public void load(Element element) {
    setHost(element.getAttributeValue(HOST));
    setLiteralPort(element.getAttributeValue(PORT));
    setAnonymous(StringUtil.parseBoolean(element.getAttributeValue(ANONYMOUS), false));
    setSerializedUserName(element.getAttributeValue(USERNAME));
    setSerializedPassword(element.getAttributeValue(PASSWORD));
    setPrivateKeyFile(StringUtil.nullize(element.getAttributeValue(PRIVATE_KEY_FILE)));
    setKnownHostsFile(StringUtil.nullize(element.getAttributeValue(KNOWN_HOSTS_FILE)));
    setSerializedPassphrase(element.getAttributeValue(PASSPHRASE));
    setUseKeyPair(StringUtil.parseBoolean(element.getAttributeValue(USE_KEY_PAIR), false));
  }

  public void save(Element rootElement) {
    rootElement.setAttribute(HOST, StringUtil.notNullize(getHost()));
    rootElement.setAttribute(PORT, getLiteralPort());
    rootElement.setAttribute(ANONYMOUS, Boolean.toString(isAnonymous()));
    rootElement.setAttribute(USERNAME, getSerializedUserName());
    rootElement.setAttribute(PASSWORD, getSerializedPassword());
    rootElement.setAttribute(PRIVATE_KEY_FILE, StringUtil.notNullize(getPrivateKeyFile()));
    rootElement.setAttribute(KNOWN_HOSTS_FILE, StringUtil.notNullize(getKnownHostsFile()));
    rootElement.setAttribute(PASSPHRASE, getSerializedPassphrase());
    rootElement.setAttribute(USE_KEY_PAIR, Boolean.toString(isUseKeyPair()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteCredentialsHolder holder = (RemoteCredentialsHolder)o;

    if (myLiteralPort != null ? !myLiteralPort.equals(holder.myLiteralPort) : holder.myLiteralPort != null) return false;
    if (myAnonymous != holder.myAnonymous) return false;
    if (myUseKeyPair != holder.myUseKeyPair) return false;
    if (myStorePassword != holder.myStorePassword) return false;
    if (myStorePassphrase != holder.myStorePassphrase) return false;
    if (myHost != null ? !myHost.equals(holder.myHost) : holder.myHost != null) return false;
    if (myUserName != null ? !myUserName.equals(holder.myUserName) : holder.myUserName != null) return false;
    if (myPassword != null ? !myPassword.equals(holder.myPassword) : holder.myPassword != null) return false;
    if (myPrivateKeyFile != null ? !myPrivateKeyFile.equals(holder.myPrivateKeyFile) : holder.myPrivateKeyFile != null) return false;
    if (myKnownHostsFile != null ? !myKnownHostsFile.equals(holder.myKnownHostsFile) : holder.myKnownHostsFile != null) return false;
    if (myPassphrase != null ? !myPassphrase.equals(holder.myPassphrase) : holder.myPassphrase != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myHost != null ? myHost.hashCode() : 0;
    result = 31 * result + (myLiteralPort != null ? myLiteralPort.hashCode() : 0);
    result = 31 * result + (myAnonymous ? 1 : 0);
    result = 31 * result + (myUserName != null ? myUserName.hashCode() : 0);
    result = 31 * result + (myPassword != null ? myPassword.hashCode() : 0);
    result = 31 * result + (myUseKeyPair ? 1 : 0);
    result = 31 * result + (myPrivateKeyFile != null ? myPrivateKeyFile.hashCode() : 0);
    result = 31 * result + (myKnownHostsFile != null ? myKnownHostsFile.hashCode() : 0);
    result = 31 * result + (myPassphrase != null ? myPassphrase.hashCode() : 0);
    result = 31 * result + (myStorePassword ? 1 : 0);
    result = 31 * result + (myStorePassphrase ? 1 : 0);
    return result;
  }
}
