/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.jna.DisposableMemory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Method;

/**
 * @author Alexander Lobas
 */
class Win7TaskBar {
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

  public interface User32Ex extends StdCallLibrary {
    User32Ex INSTANCE = (User32Ex)Native.loadLibrary("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

    int LookupIconIdFromDirectoryEx(Memory presbits, boolean fIcon, int cxDesired, int cyDesired, int Flags);

    WinDef.HICON CreateIconFromResourceEx(Pointer presbits,
                                          WinDef.DWORD dwResSize,
                                          boolean fIcon,
                                          WinDef.DWORD dwVer,
                                          int cxDesired,
                                          int cyDesired,
                                          int Flags);

    boolean FlashWindow(WinDef.HWND hwnd, boolean bInvert);
  }

  private static boolean ourInitialized = true;

  static {
    try {
      initialize();
    }
    catch (Throwable e) {
      LOG.error(e);
      ourInitialized = false;
    }
  }

  private static void initialize() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    Ole32 ole32 = Ole32.INSTANCE;
    ole32.CoInitializeEx(Pointer.NULL, 0);

    Guid.GUID CLSID_TaskbarList = Ole32Util.getGUIDFromString("{56FDF344-FD6D-11d0-958A-006097C9A090}");
    Guid.GUID IID_ITaskbarList3 = Ole32Util.getGUIDFromString("{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}");
    PointerByReference p = new PointerByReference();
    WinNT.HRESULT hr = ole32.CoCreateInstance(CLSID_TaskbarList, Pointer.NULL, ObjBase.CLSCTX_ALL, IID_ITaskbarList3, p);
    if (!W32Errors.S_OK.equals(hr)) {
      LOG.error("Win7TaskBar CoCreateInstance(IID_ITaskbarList3) hResult: " + hr);
      ourInitialized = false;
      return;
    }

    myInterfacePointer = p.getValue();
    Pointer vTablePointer = myInterfacePointer.getPointer(0);
    Pointer[] vTable = new Pointer[TaskBarList_Methods];
    vTablePointer.read(0, vTable, 0, vTable.length);

    mySetProgressValue = Function.getFunction(vTable[TaskBarList_SetProgressValue], Function.ALT_CONVENTION);
    mySetProgressState = Function.getFunction(vTable[TaskBarList_SetProgressState], Function.ALT_CONVENTION);
    mySetOverlayIcon = Function.getFunction(vTable[TaskBarList_SetOverlayIcon], Function.ALT_CONVENTION);
  }

  static void setProgress(IdeFrame frame, double value, boolean isOk) {
    if (!isEnabled()) {
      return;
    }

    WinDef.HWND handle = getHandle(frame);
    mySetProgressState.invokeInt(new Object[]{myInterfacePointer, handle, isOk ? TBPF_NORMAL : TBPF_ERROR});
    mySetProgressValue.invokeInt(new Object[]{myInterfacePointer, handle, new WinDef.ULONGLONG((long)(value * 100)), TOTAL_PROGRESS});
  }

  private static boolean isEnabled() {
    return !ApplicationManager.getApplication().isUnitTestMode() && ourInitialized;
  }

  static void hideProgress(IdeFrame frame) {
    if (!isEnabled()) {
      return;
    }

    mySetProgressState.invokeInt(new Object[]{myInterfacePointer, getHandle(frame), TBPF_NOPROGRESS});
  }

  static void setOverlayIcon(IdeFrame frame, Object icon, boolean dispose) {
    if (!isEnabled()) {
      return;
    }

    if (icon == null) {
      icon = Pointer.NULL;
    }
    mySetOverlayIcon.invokeInt(new Object[]{myInterfacePointer, getHandle(frame), icon, Pointer.NULL});
    if (dispose) {
      User32.INSTANCE.DestroyIcon((WinDef.HICON)icon);
    }
  }

  static Object createIcon(byte[] ico) {
    if (!isEnabled()) {
      return new Object();
    }

    DisposableMemory memory = new DisposableMemory(ico.length);

    try {
      memory.write(0, ico, 0, ico.length);

      int nSize = 100;
      int offset = User32Ex.INSTANCE.LookupIconIdFromDirectoryEx(memory, true, nSize, nSize, 0);
      if (offset != 0) {
        return User32Ex.INSTANCE.CreateIconFromResourceEx(memory.share(offset), DWORD_ZERO, true, ICO_VERSION, nSize, nSize, 0);
      }
      return null;
    }
    finally {
      memory.dispose();
    }
  }

  static void attention(IdeFrame frame, boolean critical) {
    if (!isEnabled()) {
      return;
    }

    User32Ex.INSTANCE.FlashWindow(getHandle(frame), true);
  }

  private static WinDef.HWND getHandle(IdeFrame frame) {
    try {
      ComponentPeer peer = ((Component)frame).getPeer();
      Method getHWnd = peer.getClass().getMethod("getHWnd");
      return new WinDef.HWND(new Pointer((Long)getHWnd.invoke(peer)));
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }
}