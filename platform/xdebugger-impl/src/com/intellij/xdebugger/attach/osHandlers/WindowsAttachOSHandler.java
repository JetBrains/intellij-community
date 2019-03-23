// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.attach.osHandlers;

import com.intellij.xdebugger.attach.EnvironmentAwareHost;
import com.intellij.xdebugger.attach.LocalAttachHost;
import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WindowsAttachOSHandler extends AttachOSHandler {
  public WindowsAttachOSHandler(@NotNull EnvironmentAwareHost host) {
    super(host, OSType.WINDOWS);
  }

  @Nullable
  @Override
  protected String getenv(String name) throws Exception {
    if(myHost instanceof LocalAttachHost) {
      return super.getenv(name);
    }

    throw new NotImplementedException();
  }
}
