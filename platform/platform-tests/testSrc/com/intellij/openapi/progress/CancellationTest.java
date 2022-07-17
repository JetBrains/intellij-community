// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.ApplicationExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.swing.*;

public abstract class CancellationTest {

  @RegisterExtension
  public static final ApplicationExtension applicationExtension = new ApplicationExtension();

  @BeforeAll
  public static void initProgressManager() {
    ProgressManager.getInstance();
  }

  @BeforeEach
  public void clearEventQueue() throws Exception {
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE);
  }
}
