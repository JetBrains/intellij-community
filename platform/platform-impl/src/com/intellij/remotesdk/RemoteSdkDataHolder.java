package com.intellij.remotesdk;

import com.intellij.openapi.util.PasswordUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
* @author traff
*/
public class RemoteSdkDataHolder implements RemoteSdkData {

  public static final String SSH_PREFIX = "ssh://";
  private static final String HOST = "HOST";
  private static final String PORT = "PORT";
  private static final String ANONYMOUS = "ANONYMOUS";
  private static final String USERNAME = "USERNAME";
  private static final String PASSWORD = "PASSWORD";
  private static final String USE_KEY_PAIR = "USE_KEY_PAIR";
  private static final String PRIVATE_KEY_FILE = "PRIVATE_KEY_FILE";
  private static final String KNOWN_HOSTS_FILE = "MY_KNOWN_HOSTS_FILE";
  private static final String PASSPHRASE = "PASSPHRASE";
  private static final String INTERPRETER_PATH = "INTERPRETER_PATH";
  private static final String HELPERS_PATH = "HELPERS_PATH";
  private static final String REMOTE_ROOTS = "REMOTE_ROOTS";
  private static final String REMOTE_PATH = "REMOTE_PATH";

  private String myHost;
  private int myPort;
  private boolean myAnonymous;
  private String myUserName;
  private String myPassword;
  private boolean myUseKeyPair;
  private String myPrivateKeyFile;
  private String myKnownHostsFile;
  private String myPassphrase;
  private boolean myStorePassword;
  private boolean myStorePassphrase;

  private String myInterpreterPath;
  private String myHelpersPath;

  private final String myHelpersDefaultDirName;

  private boolean myHelpersVersionChecked = false;

  private List<String> myRemoteRoots = new ArrayList<String>();

  public RemoteSdkDataHolder(@NotNull final String defaultDirName) {
    myHelpersDefaultDirName = defaultDirName;
  }

  @Override
  public String getInterpreterPath() {
    return myInterpreterPath;
  }

  @Override
  public void setInterpreterPath(String interpreterPath) {
    myInterpreterPath = interpreterPath;
  }


  @Override
  public String getFullInterpreterPath() {
    return SSH_PREFIX + myUserName + "@" + myHost + ":" + myPort + myInterpreterPath;
  }

  @Override
  public String getHelpersPath() {
    return myHelpersPath;
  }

  @Override
  public void setHelpersPath(String helpersPath) {
    myHelpersPath = helpersPath;
  }

  public String getDefaultHelpersName() {
    return myHelpersDefaultDirName;
  }

  @Override
  public String getHost() {
    return myHost;
  }

  @Override
  public void setHost(String host) {
    myHost = host;
  }

  @Override
  public int getPort() {
    return myPort;
  }

  @Override
  public void setPort(int port) {
    myPort = port;
  }

  @Override
  @Transient
  public String getUserName() {
    return myUserName;
  }

  @Override
  public void setUserName(String userName) {
    myUserName = userName;
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

  @Override
  public String getPassword() {
    return myPassword;
  }

  @Override
  public void setPassword(String password) {
    myPassword = password;
  }

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
  public boolean isAnonymous() {
    return myAnonymous;
  }

  @Override
  public void setAnonymous(boolean anonymous) {
    myAnonymous = anonymous;
  }

  @Override
  public String getPrivateKeyFile() {
    return myPrivateKeyFile;
  }

  @Override
  public void setPrivateKeyFile(String privateKeyFile) {
    myPrivateKeyFile = privateKeyFile;
  }


  @Override
  public String getKnownHostsFile() {
    return myKnownHostsFile;
  }

  @Override
  public void setKnownHostsFile(String knownHostsFile) {
    myKnownHostsFile = knownHostsFile;
  }

  @Override
  @Transient
  public String getPassphrase() {
    return myPassphrase;
  }

  @Override
  public void setPassphrase(String passphrase) {
    myPassphrase = passphrase;
  }

  @Nullable
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

  @Override
  public boolean isUseKeyPair() {
    return myUseKeyPair;
  }

  @Override
  public void setUseKeyPair(boolean useKeyPair) {
    myUseKeyPair = useKeyPair;
  }

  @Override
  public void addRemoteRoot(String remoteRoot) {
    myRemoteRoots.add(remoteRoot);
  }

  @Override
  public void clearRemoteRoots() {
    myRemoteRoots.clear();
  }

  @Override
  public List<String> getRemoteRoots() {
    return myRemoteRoots;
  }

  @Override
  public void setRemoteRoots(List<String> remoteRoots) {
    myRemoteRoots = remoteRoots;
  }

  @Override
  public boolean isHelpersVersionChecked() {
    return myHelpersVersionChecked;
  }

  @Override
  public void setHelpersVersionChecked(boolean helpersVersionChecked) {
    myHelpersVersionChecked = helpersVersionChecked;
  }

  public static boolean isRemoteSdk(@Nullable String path) {
    if (path != null) {
      return path.startsWith(SSH_PREFIX);
    }
    else {
      return false;
    }
  }

  public void loadRemoteSdkData(Element element) {
    setHost(element.getAttributeValue(HOST));
    setPort(StringUtil.parseInt(element.getAttributeValue(PORT), 22));
    setAnonymous(StringUtil.parseBoolean(element.getAttributeValue(ANONYMOUS), false));
    setSerializedUserName(element.getAttributeValue(USERNAME));
    setSerializedPassword(element.getAttributeValue(PASSWORD));
    setPrivateKeyFile(StringUtil.nullize(element.getAttributeValue(PRIVATE_KEY_FILE)));
    setKnownHostsFile(StringUtil.nullize(element.getAttributeValue(KNOWN_HOSTS_FILE)));
    setSerializedPassphrase(element.getAttributeValue(PASSPHRASE));
    setUseKeyPair(StringUtil.parseBoolean(element.getAttributeValue(USE_KEY_PAIR), false));

    setInterpreterPath(StringUtil.nullize(element.getAttributeValue(INTERPRETER_PATH)));
    setHelpersPath(StringUtil.nullize(element.getAttributeValue(HELPERS_PATH)));

    setRemoteRoots(loadStringsList(element, REMOTE_ROOTS, REMOTE_PATH));
  }

  protected static List<String> loadStringsList(Element element, String rootName, String attrName) {
    final List<String> paths = new LinkedList<String>();
    if (element != null) {
      @NotNull final List list = element.getChildren(rootName);
      for (Object o : list) {
        paths.add(((Element)o).getAttribute(attrName).getValue());
      }
    }
    return paths;
  }

  public void saveRemoteSdkData(Element rootElement) {
    rootElement.setAttribute(HOST, StringUtil.notNullize(getHost()));
    rootElement.setAttribute(PORT, Integer.toString(getPort()));
    rootElement.setAttribute(ANONYMOUS, Boolean.toString(isAnonymous()));
    rootElement.setAttribute(USERNAME, getSerializedUserName());
    rootElement.setAttribute(PASSWORD, getSerializedPassword());
    rootElement.setAttribute(PRIVATE_KEY_FILE, StringUtil.notNullize(getPrivateKeyFile()));
    rootElement.setAttribute(KNOWN_HOSTS_FILE, StringUtil.notNullize(getKnownHostsFile()));
    rootElement.setAttribute(PASSPHRASE, getSerializedPassphrase());
    rootElement.setAttribute(USE_KEY_PAIR, Boolean.toString(isUseKeyPair()));

    rootElement.setAttribute(INTERPRETER_PATH, StringUtil.notNullize(getInterpreterPath()));
    rootElement.setAttribute(HELPERS_PATH, StringUtil.notNullize(getHelpersPath()));

    for (String remoteRoot : getRemoteRoots()) {
      final Element child = new Element(REMOTE_ROOTS);
      child.setAttribute(REMOTE_PATH, remoteRoot);
      rootElement.addContent(child);
    }
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RemoteSdkDataHolder holder = (RemoteSdkDataHolder)o;

    if (myAnonymous != holder.myAnonymous) return false;
    if (myHelpersVersionChecked != holder.myHelpersVersionChecked) return false;
    if (myPort != holder.myPort) return false;
    if (myStorePassphrase != holder.myStorePassphrase) return false;
    if (myStorePassword != holder.myStorePassword) return false;
    if (myUseKeyPair != holder.myUseKeyPair) return false;
    if (myHost != null ? !myHost.equals(holder.myHost) : holder.myHost != null) return false;
    if (myInterpreterPath != null ? !myInterpreterPath.equals(holder.myInterpreterPath) : holder.myInterpreterPath != null) return false;
    if (myKnownHostsFile != null ? !myKnownHostsFile.equals(holder.myKnownHostsFile) : holder.myKnownHostsFile != null) return false;
    if (myPassphrase != null ? !myPassphrase.equals(holder.myPassphrase) : holder.myPassphrase != null) return false;
    if (myPassword != null ? !myPassword.equals(holder.myPassword) : holder.myPassword != null) return false;
    if (myPrivateKeyFile != null ? !myPrivateKeyFile.equals(holder.myPrivateKeyFile) : holder.myPrivateKeyFile != null) return false;
    if (myHelpersPath != null
        ? !myHelpersPath.equals(holder.myHelpersPath)
        : holder.myHelpersPath != null) {
      return false;
    }
    if (myRemoteRoots != null ? !myRemoteRoots.equals(holder.myRemoteRoots) : holder.myRemoteRoots != null) return false;
    if (myUserName != null ? !myUserName.equals(holder.myUserName) : holder.myUserName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myHost != null ? myHost.hashCode() : 0;
    result = 31 * result + myPort;
    result = 31 * result + (myAnonymous ? 1 : 0);
    result = 31 * result + (myUserName != null ? myUserName.hashCode() : 0);
    result = 31 * result + (myPassword != null ? myPassword.hashCode() : 0);
    result = 31 * result + (myUseKeyPair ? 1 : 0);
    result = 31 * result + (myPrivateKeyFile != null ? myPrivateKeyFile.hashCode() : 0);
    result = 31 * result + (myKnownHostsFile != null ? myKnownHostsFile.hashCode() : 0);
    result = 31 * result + (myPassphrase != null ? myPassphrase.hashCode() : 0);
    result = 31 * result + (myStorePassword ? 1 : 0);
    result = 31 * result + (myStorePassphrase ? 1 : 0);
    result = 31 * result + (myInterpreterPath != null ? myInterpreterPath.hashCode() : 0);
    result = 31 * result + (myHelpersPath != null ? myHelpersPath.hashCode() : 0);
    result = 31 * result + (myHelpersVersionChecked ? 1 : 0);
    result = 31 * result + (myRemoteRoots != null ? myRemoteRoots.hashCode() : 0);
    return result;
  }
}
