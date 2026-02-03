// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@SuppressWarnings("UnusedReturnValue")
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

  boolean FlashWindow(WinDef.HWND hwnd, boolean bInvert);

  boolean SystemParametersInfo(WinDef.UINT uiAction, WinDef.UINT uiParam, WinDef.BOOLByReference pvParam, WinDef.UINT fWinIni);

  boolean SystemParametersInfo(WinDef.UINT uiAction, WinDef.UINT uiParam, WinDef.UINTByReference pvParam, WinDef.UINT fWinIni);

  boolean SystemParametersInfo(WinDef.UINT uiAction, WinDef.UINT uiParam, WinDef.UINT pvParam, WinDef.UINT fWinIni);

  boolean AllowSetForegroundWindow(WinDef.DWORD pid);

  boolean SetForegroundWindow(WinDef.HWND hwnd);

  @ApiStatus.Internal
  boolean EnumWindows(@NotNull EnumThreadWindowsCallback callback, @Nullable WinDef.INT_PTR extraData);

  @ApiStatus.Internal
  boolean GetWindowThreadProcessId(WinDef.HWND handle, IntByReference lpdwProcessId);

  @ApiStatus.Internal
  WinDef.HWND GetWindow(WinDef.HWND hWnd, int uCmd);

  @ApiStatus.Internal
  boolean IsWindowVisible(WinDef.HWND hWnd);

  @ApiStatus.Internal
  int GetWindowTextLength(WinDef.HWND hWnd);

  @ApiStatus.Internal
  int GetWindowText(WinDef.HWND hWnd, char[] text, int maxLength);

  @ApiStatus.Internal
  interface EnumThreadWindowsCallback extends Callback {
    boolean callback(WinDef.HWND hWnd, IntByReference lParam);
  }
}
