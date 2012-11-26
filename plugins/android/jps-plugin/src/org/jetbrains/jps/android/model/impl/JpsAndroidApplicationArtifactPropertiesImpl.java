package org.jetbrains.jps.android.model.impl;

import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidApplicationArtifactProperties;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidApplicationArtifactPropertiesImpl extends JpsElementBase<JpsAndroidApplicationArtifactPropertiesImpl>
  implements JpsAndroidApplicationArtifactProperties {

  public static final JpsElementChildRole<JpsAndroidApplicationArtifactProperties> ROLE =
    JpsElementChildRoleBase.create("android application artifact properties");

  private final MyState myState = new MyState();

  public JpsAndroidApplicationArtifactPropertiesImpl() {
  }

  public JpsAndroidApplicationArtifactPropertiesImpl(@NotNull MyState state) {
    myState.SIGNING_MODE = state.SIGNING_MODE;
    myState.KEY_STORE_URL = state.KEY_STORE_URL;
    myState.KEY_STORE_PASSWORD = state.KEY_STORE_PASSWORD;
    myState.KEY_ALIAS = state.KEY_ALIAS;
    myState.KEY_PASSWORD = state.KEY_PASSWORD;
    myState.PROGUARD_CFG_FILE_URL = state.PROGUARD_CFG_FILE_URL;
    myState.RUN_PROGUARD = state.RUN_PROGUARD;
    myState.INCLUDE_SYSTEM_PROGUARD_CFG_FILE = state.INCLUDE_SYSTEM_PROGUARD_CFG_FILE;
  }

  @NotNull
  @Override
  public JpsAndroidApplicationArtifactPropertiesImpl createCopy() {
    return new JpsAndroidApplicationArtifactPropertiesImpl(myState);
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidApplicationArtifactPropertiesImpl modified) {
    setSigningMode(modified.getSigningMode());
    setKeyStoreUrl(modified.getKeyStoreUrl());
    setKeyStorePassword(modified.getKeyStorePassword());
    setKeyAlias(modified.getKeyAlias());
    setKeyPassword(modified.getKeyPassword());
    setRunProGuard(modified.isRunProGuard());
    setProGuardCfgFileUrl(modified.getProGuardCfgFileUrl());
    setIncludeSystemProGuardCfgFile(modified.isIncludeSystemProGuardCfgFile());
  }

  @NotNull
  public MyState getState() {
    return myState;
  }

  @Override
  public AndroidArtifactSigningMode getSigningMode() {
    return myState.SIGNING_MODE;
  }

  @Override
  public void setSigningMode(AndroidArtifactSigningMode mode) {
    if (!myState.SIGNING_MODE.equals(mode)) {
      myState.SIGNING_MODE = mode;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyStoreUrl() {
    return myState.KEY_STORE_URL;
  }

  @Override
  public void setKeyStoreUrl(String url) {
    if (!myState.KEY_STORE_URL.equals(url)) {
      myState.KEY_STORE_URL = url;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyStorePassword() {
    return myState.KEY_STORE_PASSWORD;
  }

  @Override
  public void setKeyStorePassword(String password) {
    if (!myState.KEY_STORE_PASSWORD.equals(password)) {
      myState.KEY_STORE_PASSWORD = password;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyAlias() {
    return myState.KEY_ALIAS;
  }

  @Override
  public void setKeyAlias(String alias) {
    if (!myState.KEY_ALIAS.equals(alias)) {
      myState.KEY_ALIAS = alias;
      fireElementChanged();
    }
  }

  @Override
  public String getKeyPassword() {
    return myState.KEY_PASSWORD;
  }

  @Override
  public void setKeyPassword(String password) {
    if (!myState.KEY_PASSWORD.equals(password)) {
      myState.KEY_PASSWORD = password;
      fireElementChanged();
    }
  }

  @Override
  public boolean isRunProGuard() {
    return myState.RUN_PROGUARD;
  }

  @Override
  public void setRunProGuard(boolean value) {
    if (myState.RUN_PROGUARD != value) {
      myState.RUN_PROGUARD = value;
      fireElementChanged();
    }
  }

  @Override
  public String getProGuardCfgFileUrl() {
    return myState.PROGUARD_CFG_FILE_URL;
  }

  @Override
  public void setProGuardCfgFileUrl(String url) {
    if (!myState.PROGUARD_CFG_FILE_URL.equals(url)) {
      myState.PROGUARD_CFG_FILE_URL = url;
      fireElementChanged();
    }
  }

  @Override
  public boolean isIncludeSystemProGuardCfgFile() {
    return myState.INCLUDE_SYSTEM_PROGUARD_CFG_FILE;
  }

  @Override
  public void setIncludeSystemProGuardCfgFile(boolean value) {
    if (myState.INCLUDE_SYSTEM_PROGUARD_CFG_FILE != value) {
      myState.INCLUDE_SYSTEM_PROGUARD_CFG_FILE = value;
      fireElementChanged();
    }
  }

  public static class MyState {
    public AndroidArtifactSigningMode SIGNING_MODE = AndroidArtifactSigningMode.RELEASE_UNSIGNED;
    public String KEY_STORE_URL = "";
    public String KEY_STORE_PASSWORD = "";
    public String KEY_ALIAS = "";
    public String KEY_PASSWORD = "";
    public boolean RUN_PROGUARD;
    public String PROGUARD_CFG_FILE_URL = "";
    public boolean INCLUDE_SYSTEM_PROGUARD_CFG_FILE;
  }
}