// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io.win32;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;

public class WindowsElevationUtil {
  private static final Logger LOG = Logger.getInstance(WindowsElevationUtil.class);

  private WindowsElevationUtil() { }

  /**
   * Returns {@code true} if this process is running under elevated privileges.
   */
  public static boolean isUnderElevation() {
    return SystemInfo.isWindows && JnaLoader.isLoaded() && Holder.elevated;
  }

  private static class Holder {
    static final boolean elevated;

    static {
      boolean result = false;
      try {
        result = isElevated();
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
      elevated = result;
    }

    private static boolean isElevated() {
      WinNT.HANDLEByReference tokenHandle = new WinNT.HANDLEByReference();

      boolean rc = Advapi32.INSTANCE.OpenProcessToken(
        Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_ADJUST_PRIVILEGES | WinNT.TOKEN_QUERY, tokenHandle);
      if (!rc) {
        int lastError = Kernel32.INSTANCE.GetLastError();
        LOG.warn("OpenProcessToken: " + lastError + ' ' + Kernel32Util.formatMessageFromLastErrorCode(lastError));
        return false;
      }

      try {
        IntByReference cbNeeded = new IntByReference(0);
        TOKEN_ELEVATION tokenElevation = new TOKEN_ELEVATION();
        rc = Advapi32.INSTANCE.GetTokenInformation(
          tokenHandle.getValue(), WinNT.TOKEN_INFORMATION_CLASS.TokenElevation, tokenElevation, tokenElevation.size(), cbNeeded);
        if (!rc) {
          int lastError = Kernel32.INSTANCE.GetLastError();
          LOG.warn("GetTokenInformation: " + lastError + ' ' + Kernel32Util.formatMessageFromLastErrorCode(lastError));
          return false;
        }

        return tokenElevation.TokenIsElevated.intValue() != 0;
      }
      finally {
        Kernel32.INSTANCE.CloseHandle(tokenHandle.getValue());
      }
    }

    @Structure.FieldOrder("TokenIsElevated")
    public static class TOKEN_ELEVATION extends Structure {
      public WinDef.DWORD TokenIsElevated = new WinDef.DWORD(0);
    }
  }
}