// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.packaging;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JavaFxPackagerConstants {
  public static final @NonNls String UPDATE_MODE_BACKGROUND = "background";
  public static final @NonNls String UPDATE_MODE_ALWAYS = "always";
  public static final @NonNls String DEFAULT_HEIGHT = "400";
  public static final @NonNls String DEFAULT_WEIGHT = "600";

  public enum NativeBundles {
    none, all, deb, dmg, exe, image, msi, rpm;

    public boolean isOnLinux() {
      return this == all || this == deb || this == rpm;
    }

    public boolean isOnMac() {
      return this == all || this == dmg;
    }

    public boolean isOnWindows() {
      return this == all || this == exe || this == msi;
    }
  }

  public enum MsgOutputLevel {
    Quiet("-quiet", false), Default("", false), Verbose("-verbose", true), Debug("-debug", true);

    private final String myCmdLineParam;
    private final boolean myIsVerbose;

    MsgOutputLevel(String cmdLineParam, boolean isVerbose) {
      myCmdLineParam = cmdLineParam;
      myIsVerbose = isVerbose;
    }

    public @NotNull String getCmdLineParam() {
      return myCmdLineParam;
    }

    public boolean isVerbose() {
      return myIsVerbose;
    }
  }
}
