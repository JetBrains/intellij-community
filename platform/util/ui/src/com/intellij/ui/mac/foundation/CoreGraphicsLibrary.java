// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface CoreGraphicsLibrary extends Library {
  ID CGWindowListCreateImage(CoreGraphics.CGRect screenBounds, int windowOption, ID windowID, int imageOption);

  ID objc_getClass(String className);
  Pointer sel_registerName(String selectorName);
}
