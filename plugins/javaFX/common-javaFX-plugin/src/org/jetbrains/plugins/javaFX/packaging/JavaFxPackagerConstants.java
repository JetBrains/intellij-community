// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JavaFxPackagerConstants {
  @NonNls public static final String UPDATE_MODE_BACKGROUND = "background";
  @NonNls public static final String UPDATE_MODE_ALWAYS = "always";
  @NonNls public static final String DEFAULT_HEIGHT = "400";
  @NonNls public static final String DEFAULT_WEIGHT = "600";

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

    @NotNull
    public String getCmdLineParam() {
      return myCmdLineParam;
    }

    public boolean isVerbose() {
      return myIsVerbose;
    }
  }
}
