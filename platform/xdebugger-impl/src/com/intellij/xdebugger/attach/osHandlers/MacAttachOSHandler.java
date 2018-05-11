// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.osHandlers;

import com.intellij.xdebugger.attach.EnvironmentAwareHost;
import org.jetbrains.annotations.NotNull;

class MacAttachOSHandler extends UnixAttachOSHandler {
  public MacAttachOSHandler(@NotNull EnvironmentAwareHost host) {
    super(host, OSType.MACOSX);
  }
}