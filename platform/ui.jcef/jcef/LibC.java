// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.sun.jna.Library;
import com.sun.jna.Native;

interface LibC extends Library {
  LibC INSTANCE = Native.load("c", LibC.class);

  String gnu_get_libc_version();
}
