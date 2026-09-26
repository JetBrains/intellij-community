package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public final class WindowsComException extends IOException {
  public final int hresult;

  WindowsComException(@NotNull String function, int hresult) {
    super(function + " failed with HRESULT 0x" + Integer.toHexString(hresult));
    this.hresult = hresult;
  }
}
