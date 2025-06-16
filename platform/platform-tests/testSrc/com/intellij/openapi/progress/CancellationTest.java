// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import javax.swing.*;

@TestApplication
public abstract class CancellationTest {

  @BeforeAll
  public static void initProgressManager() {
    ProgressManager.getInstance();
  }

  @BeforeEach
  public void clearEventQueue() throws Exception {
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE);
  }
}
