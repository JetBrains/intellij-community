/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
                                       @NotNull @NonNls String appName,
                                       @Nullable Splash splash) {
    new ApplicationImpl(internal, isUnitTestMode, isHeadlessMode, isCommandline, appName, splash);
  }
}
