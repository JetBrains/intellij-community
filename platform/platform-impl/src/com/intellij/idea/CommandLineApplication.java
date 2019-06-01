// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;

/** @deprecated obsolete; use {@link com.intellij.ide.impl.HeadlessDataManager} and {@code com.intellij.idea.IdeaTestApplication} instead */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
public class CommandLineApplication {
  private static final Logger LOG = Logger.getInstance("#com.intellij.idea.CommandLineApplication");

  protected static CommandLineApplication ourInstance;

  protected CommandLineApplication(boolean isInternal, boolean isUnitTestMode, boolean isHeadless) {
    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourInstance = this;
    new ApplicationImpl(isInternal, isUnitTestMode, isHeadless, true, ApplicationManagerEx.IDEA_APPLICATION);
  }
}