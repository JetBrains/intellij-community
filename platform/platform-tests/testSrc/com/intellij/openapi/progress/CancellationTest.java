// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.testFramework.ApplicationExtension;
import com.intellij.testFramework.UncaughtExceptionsExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class CancellationTest {

  @RegisterExtension
  public static final ApplicationExtension applicationExtension = new ApplicationExtension();

  @RegisterExtension
  public static final UncaughtExceptionsExtension uncaughtExceptionsExtension = new UncaughtExceptionsExtension();

  @BeforeAll
  public static void initProgressManager() {
    ProgressManager.getInstance();
  }
}
