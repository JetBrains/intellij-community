package org.jetbrains.jps.android.model;

import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author Eugene.Kudelevsky
 */
public interface JpsAndroidApplicationArtifactProperties extends JpsElement {

  AndroidArtifactSigningMode getSigningMode();

  void setSigningMode(AndroidArtifactSigningMode mode);

  String getKeyStoreUrl();

  void setKeyStoreUrl(String url);

  String getKeyStorePassword();

  void setKeyStorePassword(String password);

  String getKeyAlias();

  void setKeyAlias(String alias);

  String getKeyPassword();

  void setKeyPassword(String password);

  boolean isRunProGuard();

  void setRunProGuard(boolean value);

  String getProGuardCfgFileUrl();

  void setProGuardCfgFileUrl(String url);

  boolean isIncludeSystemProGuardCfgFile();

  void setIncludeSystemProGuardCfgFile(boolean value);
}
