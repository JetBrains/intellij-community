package com.intellij.openapi.util.io.win32;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WindowsElevationUtil {
  private static Logger LOG = Logger.getInstance(WindowsElevationUtil.class);

  private WindowsElevationUtil() {
  }

  /**
   * returns true if current user is having administrator privileges AND this code is running under elevation
   */
  public static boolean isUnderElevation() {
    return IS_UNDER_ELEVATION.getValue();
  }

  private static final AtomicNotNullLazyValue<Boolean> IS_UNDER_ELEVATION = new AtomicNotNullLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      if (!SystemInfo.isWindows) {
        return false;
      }

      try {
        return isElevated();
      } catch (Throwable t) {
        LOG.warn("Unexpected exception in WindowsElevationUtil.IS_UNDER_ELEVATION", t);
        return false;
      }
    }

    private boolean isElevated() {
      WinNT.HANDLEByReference processTokenHandle = new WinNT.HANDLEByReference();

      boolean openProcessRc = Advapi32.INSTANCE.OpenProcessToken(
        Kernel32.INSTANCE.GetCurrentProcess(),
        WinNT.TOKEN_ADJUST_PRIVILEGES | WinNT.TOKEN_QUERY, processTokenHandle);
      if (!openProcessRc) {
        int lastError = Kernel32.INSTANCE.GetLastError();
        LOG.warn("Unable to OpenProcessToken: error " + lastError + ": " + Kernel32Util.formatMessageFromLastErrorCode(lastError));
        return false;
      }

      try {
        IntByReference cbNeeded = new IntByReference(0);
        TOKEN_ELEVATION tokenElevation = new TOKEN_ELEVATION();
        boolean getTokenInformationRc = Advapi32.INSTANCE.GetTokenInformation(
          processTokenHandle.getValue(), WinNT.TOKEN_INFORMATION_CLASS.TokenElevation,
          tokenElevation, tokenElevation.size(), cbNeeded);
        if (!getTokenInformationRc) {
          int lastError = Kernel32.INSTANCE.GetLastError();
          LOG.warn("Unable to GetTokenInformation: error " + lastError + ": " + Kernel32Util.formatMessageFromLastErrorCode(lastError));
          return false;
        }

        return tokenElevation.TokenIsElevated.intValue() != 0;
      } finally {
        Kernel32.INSTANCE.CloseHandle(processTokenHandle.getValue());
      }
    }
  };

  public static class TOKEN_ELEVATION extends Structure {
    @SuppressWarnings("WeakerAccess")
    public WinDef.DWORD TokenIsElevated = new WinDef.DWORD(0);

    @Override
    protected List<String> getFieldOrder() {
      return FIELDS;
    }

    private static List<String> FIELDS = createFieldsOrder("TokenIsElevated");
  }
}
