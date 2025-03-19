// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.devkit.model;

import org.jetbrains.annotations.Nullable;

public class JpsIdeaSdkProperties {
  private final String mySandboxHome;
  private final String myJdkName;

  public JpsIdeaSdkProperties(String sandboxHome, String jdkName) {
    mySandboxHome = sandboxHome;
    myJdkName = jdkName;
  }

  public @Nullable String getSandboxHome() {
    return mySandboxHome;
  }

  public @Nullable String getJdkName() {
    return myJdkName;
  }
}
