// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface User32Ex extends StdCallLibrary {
  User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

  int LookupIconIdFromDirectoryEx(Memory pResBits, boolean fIcon, int cxDesired, int cyDesired, int Flags);

  WinDef.HICON CreateIconFromResourceEx(Pointer pResBits,
                                        WinDef.DWORD dwResSize,
                                        boolean fIcon,
                                        WinDef.DWORD dwVer,
                                        int cxDesired,
                                        int cyDesired,
                                        int Flags);

  @SuppressWarnings("UnusedReturnValue")
  boolean FlashWindow(WinDef.HWND hwnd, boolean bInvert);
  boolean SystemParametersInfo(WinDef.UINT uiAction, WinDef.UINT uiParam, WinDef.BOOLByReference pvParam, WinDef.UINT fWinIni);
  boolean SystemParametersInfo(WinDef.UINT uiAction, WinDef.UINT uiParam, WinDef.UINTByReference pvParam, WinDef.UINT fWinIni);
  boolean SystemParametersInfo(WinDef.UINT uiAction, WinDef.UINT uiParam, WinDef.UINT pvParam, WinDef.UINT fWinIni);
}
