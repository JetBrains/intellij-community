// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.Executor;

public enum PlainEdtExecutor implements Executor {
  INSTANCE;

  @Override
  public void execute(@NotNull Runnable command) {
    if (SwingUtilities.isEventDispatchThread()) {
      command.run();
    }
    else {
      SwingUtilities.invokeLater(command);
    }
  }
}
