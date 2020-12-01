// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.wm.impl.SystemDock;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nikita Provotorov
 */
public class MacDockDelegateInitializer implements SystemDock.Delegate.Initializer {
  @Override
  public void onUiInitialization() {}

  @Override
  public @Nullable SystemDock.Delegate onUiInitialized() {
    return MacDockDelegate.getInstance();
  }
}
