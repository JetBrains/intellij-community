// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.EDT;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

/**
 * This class is not thread safe, and must be accessed from EDT only.
 *
 * @author Alexander Lobas
 */
final class Win7TaskBar {
  private static final Logger LOG = Logger.getInstance("Win7TaskBar");

  private static final int TaskBarList_Methods = 21;
  private static final int TaskBarList_SetProgressValue = 9;
  private static final int TaskBarList_SetProgressState = 10;
  private static final int TaskBarList_SetOverlayIcon = 18;

  private static final WinDef.DWORD ICO_VERSION = new WinDef.DWORD(0x00030000);
  private static final WinDef.DWORD DWORD_ZERO = new WinDef.DWORD(0);
  private static final WinDef.DWORD TBPF_NOPROGRESS = DWORD_ZERO;
  private static final WinDef.DWORD TBPF_NORMAL = new WinDef.DWORD(0x2);
  private static final WinDef.DWORD TBPF_ERROR = new WinDef.DWORD(0x4);
  private static final WinDef.ULONGLONG TOTAL_PROGRESS = new WinDef.ULONGLONG(100);

  private static Pointer myInterfacePointer;
  private static Function mySetProgressValue;
  private static Function mySetProgressState;
  private static Function mySetOverlayIcon;

  private static final boolean ourInitialized;
  static {
    boolean initialized = false;
    try {
      initialized = initialize();
    }
    catch (Throwable t) {
      LOG.error(t);
    }
    ourInitialized = initialized;
  }

  private static boolean initialize() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }
    EDT.assertIsEdt();

    Ole32 ole32 = Ole32.INSTANCE;
    ole32.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED);

    Guid.GUID CLSID_TaskBarList = Ole32Util.getGUIDFromString("{56FDF344-FD6D-11d0-958A-006097C9A090}");
    Guid.GUID IID_ITaskBarList3 = Ole32Util.getGUIDFromString("{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}");
    PointerByReference p = new PointerByReference();
    WinNT.HRESULT hr = ole32.CoCreateInstance(CLSID_TaskBarList, Pointer.NULL, ObjBase.CLSCTX_INPROC, IID_ITaskBarList3, p);
    if (!WinError.S_OK.equals(hr)) {
      LOG.error("Win7TaskBar CoCreateInstance(IID_ITaskBarList3) hResult: " + hr);
      return false;
    }

    myInterfacePointer = p.getValue();
    Pointer vTablePointer = myInterfacePointer.getPointer(0);
    Pointer[] vTable = new Pointer[TaskBarList_Methods];
    vTablePointer.read(0, vTable, 0, vTable.length);

    mySetProgressValue = Function.getFunction(vTable[TaskBarList_SetProgressValue], Function.ALT_CONVENTION);
    mySetProgressState = Function.getFunction(vTable[TaskBarList_SetProgressState], Function.ALT_CONVENTION);
    mySetOverlayIcon = Function.getFunction(vTable[TaskBarList_SetOverlayIcon], Function.ALT_CONVENTION);

    return true;
  }

  static void setProgress(@Nullable JFrame frame, double value, boolean isOk) {
    if (!ourInitialized || frame == null) {
      return;
    }

    WinDef.HWND handle = getHandle(frame);
    mySetProgressState.invokeInt(new Object[]{myInterfacePointer, handle, isOk ? TBPF_NORMAL : TBPF_ERROR});
    mySetProgressValue.invokeInt(new Object[]{myInterfacePointer, handle, new WinDef.ULONGLONG((long)(value * 100)), TOTAL_PROGRESS});
  }

  static void hideProgress(@NotNull JFrame frame) {
    if (!ourInitialized) {
      return;
    }

    mySetProgressState.invokeInt(new Object[]{myInterfacePointer, getHandle(frame), TBPF_NOPROGRESS});
  }

  static void setOverlayIcon(@NotNull JFrame frame, WinDef.HICON icon, boolean dispose) {
    if (!ourInitialized) {
      return;
    }

    mySetOverlayIcon.invokeInt(new Object[]{myInterfacePointer, getHandle(frame), icon, Pointer.NULL});

    if (dispose) {
      User32.INSTANCE.DestroyIcon(icon);
    }
  }

  static WinDef.HICON createIcon(byte[] ico) {
    if (!ourInitialized) {
      return null;
    }

    try (Memory memory = new Memory(ico.length)) {
      memory.write(0, ico, 0, ico.length);

      int nSize = 100;
      int offset = User32Ex.INSTANCE.LookupIconIdFromDirectoryEx(memory, true, nSize, nSize, 0);
      if (offset != 0) {
        return User32Ex.INSTANCE.CreateIconFromResourceEx(memory.share(offset), DWORD_ZERO, true, ICO_VERSION, nSize, nSize, 0);
      }

      return null;
    }
  }

  static void attention(@NotNull JFrame frame) {
    if (!ourInitialized) {
      return;
    }

    User32Ex.INSTANCE.FlashWindow(getHandle(frame), true);
  }

  static void setForegroundWindow(@NotNull Window window) {
    if (!ourInitialized || !window.isShowing()) {
      return;
    }

    User32Ex.INSTANCE.SetForegroundWindow(getHandle(window));
  }

  private static WinDef.HWND getHandle(@NotNull Window window) {
    try {
      ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(window);
      Method getHWnd = peer.getClass().getMethod("getHWnd");
      return new WinDef.HWND(new Pointer((Long)getHWnd.invoke(peer)));
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }
}
