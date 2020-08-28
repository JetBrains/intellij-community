// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.WinFocusStealer;
import org.jetbrains.annotations.NotNull;

/**
 * This class mutes focus stealing prevention mechanism on Windows, while at least one debug session is active, to make 'Focus application
 * on breakpoint' setting work as expected.
 */
public class DebuggerFocusManager implements RegistryValueListener {
  private final RegistryValue mySetting = Registry.get("debugger.mayBringFrameToFrontOnBreakpoint");
  private int mySessionCount;
  private boolean myFocusStealingEnabled;

  public DebuggerFocusManager(Disposable disposable) {
    mySetting.addListener(this, disposable);
  }

  public void debugStarted() {
    update(1);
  }

  public void debugFinished() {
    update(-1);
  }

  @Override
  public void afterValueChanged(@NotNull RegistryValue value) {
    update(0);
  }

  private synchronized void update(int sessionCountDelta) {
    mySessionCount += sessionCountDelta;
    if (mySessionCount < 0) {
      Logger.getInstance(DebuggerFocusManager.class).error("Unbalanced started/finished calls");
    }
    boolean shouldBeEnabled = mySessionCount > 0 && mySetting.asBoolean();
    if (shouldBeEnabled != myFocusStealingEnabled) {
      WinFocusStealer.setFocusStealingEnabled(myFocusStealingEnabled = shouldBeEnabled);
    }
  }
}
