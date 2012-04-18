package org.jetbrains.android.compiler;

import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class ProguardRunningOptions {
  private final String myProguardCfgFile;
  private final boolean myIncludeSystemProguardFile;

  public ProguardRunningOptions(@Nullable String proguardCfgFile, boolean includeSystemProguardFile) {
    myProguardCfgFile = proguardCfgFile;
    myIncludeSystemProguardFile = includeSystemProguardFile;
  }

  @Nullable
  public String getProguardCfgFile() {
    return myProguardCfgFile;
  }

  public boolean isIncludeSystemProguardFile() {
    return myIncludeSystemProguardFile;
  }
}
