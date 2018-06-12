// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

public class NSAutoreleaseLock implements AutoCloseable {
  private final ID myNative;

  public NSAutoreleaseLock() { myNative = Foundation.invoke("NSAutoreleasePool", "new"); }

  @Override
  public void close() { Foundation.invoke(myNative, "release"); }
}
