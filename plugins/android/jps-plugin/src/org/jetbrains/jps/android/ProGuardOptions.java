package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class ProGuardOptions {
  private final File myCfgFile;
  private final boolean myIncludeSystemCfgFile;

  public ProGuardOptions(@Nullable File cfgFile, boolean includeSystemCfgFile) {
    myCfgFile = cfgFile;
    myIncludeSystemCfgFile = includeSystemCfgFile;
  }

  @Nullable
  public File getCfgFile() {
    return myCfgFile;
  }

  public boolean isIncludeSystemCfgFile() {
    return myIncludeSystemCfgFile;
  }
}
