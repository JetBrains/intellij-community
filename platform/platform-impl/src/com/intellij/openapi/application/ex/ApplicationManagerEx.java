// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.ui.Splash;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class ApplicationManagerEx extends ApplicationManager {
  public static final String IDEA_APPLICATION = "idea";

  public static ApplicationEx getApplicationEx() {
    return (ApplicationEx) ourApplication;
  }

  /**
   * @param appName used to load default configs; if you are not sure, use {@link #IDEA_APPLICATION}.
   */
  public static void createApplication(boolean internal,
                                       boolean isUnitTestMode,
                                       boolean isHeadlessMode,
                                       boolean isCommandline,
                                       @NotNull @NonNls String appName) {
    new ApplicationImpl(internal, isUnitTestMode, isHeadlessMode, isCommandline, appName);
  }

  /**
   * @deprecated Use {@link #createApplication(boolean, boolean, boolean, boolean, String)}
   */
  @Deprecated
  public static void createApplication(boolean internal,
                                       boolean isUnitTestMode,
                                       boolean isHeadlessMode,
                                       boolean isCommandline,
                                       @NotNull @NonNls String appName,
                                       @SuppressWarnings("unused") @Nullable Splash splash) {
    createApplication(internal, isUnitTestMode, isHeadlessMode, isCommandline, appName);
  }
}
