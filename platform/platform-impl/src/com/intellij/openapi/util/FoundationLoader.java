// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.ui.mac.foundation.Foundation;

// todo: not yet clear - is it safe to make this class as ApplicationInitializedListener
public class FoundationLoader implements BaseComponent {
  @Override
  public void initComponent() {
    if (SystemInfo.isMac) {
      Foundation.init();
    }
  }
}
