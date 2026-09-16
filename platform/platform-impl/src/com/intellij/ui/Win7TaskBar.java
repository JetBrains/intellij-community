// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.ui.EDT;
import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.JFrame;
import java.awt.Window;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * This class is not thread safe, and must be accessed from EDT only.
 *
 * @author Alexander Lobas
 */
final class Win7TaskBar {
  private static final Logger LOG = Logger.getInstance("Win7TaskBar");

  private static final int ICO_VERSION = 0x00030000;
  private static final int TBPF_NOPROGRESS = 0;
  private static final int TBPF_NORMAL = 2;
  private static final int TBPF_ERROR = 4;
  private static TaskbarInterface taskbar;

  private static final boolean ourInitialized;
  static {
    boolean initialized = false;
    try {
      initialized = initialize();
    }
    catch (Throwable t) {
      LOG.warn("The Windows taskbar is unavailable", t);
    }
    ourInitialized = initialized;
  }

  private static boolean initialize() throws Throwable {
    if (!SystemInfoRt.isWindows || ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode()) {
      return false;
    }
    EDT.assertIsEdt();

    var linker = Linker.nativeLinker();
    var ole32 = WindowsSystemLibraries.lookup("ole32.dll");
    var initialize = linker.downcallHandle(ole32.findOrThrow("CoInitializeEx"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    var uninitialize = linker.downcallHandle(ole32.findOrThrow("CoUninitialize"), FunctionDescriptor.ofVoid());
    var parseGuid = linker.downcallHandle(ole32.findOrThrow("CLSIDFromString"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    var createInstance = linker.downcallHandle(ole32.findOrThrow("CoCreateInstance"),
                                              FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    var initialized = (int)initialize.invokeExact(MemorySegment.NULL, 2);
    if (initialized != 0x80010106) checkResult("CoInitializeEx", initialized);
    var success = false;
    try (var arena = Arena.ofConfined()) {
      var classId = arena.allocate(16, 4);
      var interfaceId = arena.allocate(16, 4);
      checkResult("CLSIDFromString", (int)parseGuid.invokeExact(
        arena.allocateFrom("{56FDF344-FD6D-11d0-958A-006097C9A090}", StandardCharsets.UTF_16LE), classId));
      checkResult("CLSIDFromString", (int)parseGuid.invokeExact(
        arena.allocateFrom("{EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF}", StandardCharsets.UTF_16LE), interfaceId));
      var result = arena.allocate(ADDRESS);
      checkResult("CoCreateInstance", (int)createInstance.invokeExact(classId, MemorySegment.NULL, 3, interfaceId, result));
      var pointer = result.get(ADDRESS, 0);
      if (pointer.address() == 0) return false;
      try {
        taskbar = new TaskbarInterface(pointer);
        taskbar.init();
        success = true;
        return true;
      }
      finally {
        if (!success) {
          var release = TaskbarInterface.method(pointer, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS));
          var _ = (int)release.invokeExact(pointer);
          taskbar = null;
        }
      }
    }
    finally {
      if (!success && initialized >= 0) uninitialize.invokeExact();
    }
  }

  static boolean isAvailable() { return ourInitialized; }

  static void setProgress(@Nullable JFrame frame, double value, boolean isOk) {
    if (!ourInitialized || frame == null) {
      return;
    }

    EDT.assertIsEdt();
    var handle = getHandle(frame);
    taskbar.setProgressState(handle, isOk ? TBPF_NORMAL : TBPF_ERROR);
    taskbar.setProgressValue(handle, (long)(value * 100), 100);
  }

  static void hideProgress(@NotNull JFrame frame) {
    if (!ourInitialized) {
      return;
    }

    EDT.assertIsEdt();
    taskbar.setProgressState(getHandle(frame), TBPF_NOPROGRESS);
  }

  static void setOverlayIcon(@NotNull JFrame frame, long icon, boolean dispose) {
    try {
      if (ourInitialized) {
        EDT.assertIsEdt();
        taskbar.setOverlayIcon(getHandle(frame), MemorySegment.ofAddress(icon));
      }
    }
    finally {
      if (dispose && icon != 0) User32Ex.destroyIcon(icon);
    }
  }

  static long createIcon(byte[] ico) {
    if (!ourInitialized) {
      return 0;
    }

    // CreateIconFromResourceEx copies the bits, so the arena can close right after the call
    try (var arena = Arena.ofConfined()) {
      var memory = arena.allocateFrom(ValueLayout.JAVA_BYTE, ico);

      var nSize = 100;
      var offset = User32Ex.lookupIconIdFromDirectoryEx(memory, true, nSize, nSize, 0);
      if (offset != 0) {
        var icon = User32Ex.createIconFromResourceEx(memory.asSlice(offset), 0, true, ICO_VERSION, nSize, nSize, 0);
        return icon;
      }

      return 0;
    }
  }

  static void attention(@NotNull JFrame frame) {
    if (!ourInitialized) {
      return;
    }

    User32Ex.flashWindow(getHandleValue(frame), true);
  }

  static void setForegroundWindow(@NotNull Window window) {
    if (!ourInitialized || !window.isShowing()) {
      return;
    }

    User32Ex.setForegroundWindow(getHandleValue(window));
  }

  private static MemorySegment getHandle(@NotNull Window window) {
    return MemorySegment.ofAddress(getHandleValue(window));
  }

  private static long getHandleValue(@NotNull Window window) {
    try {
      var peer = AWTAccessor.getComponentAccessor().getPeer(window);
      if (peer == null) return 0;
      var getHWnd = peer.getClass().getMethod("getHWnd");
      return (Long)getHWnd.invoke(peer);
    }
    catch (Throwable e) {
      LOG.error(e);
      return 0;
    }
  }

  private static void checkResult(String operation, int result) {
    if (result < 0) throw new IllegalStateException(operation + " failed: 0x" + Integer.toHexString(result));
  }

  static final class TaskbarInterface {
    private final MemorySegment pointer;
    private final MethodHandle init;
    private final MethodHandle progressValue;
    private final MethodHandle progressState;
    private final MethodHandle overlayIcon;

    TaskbarInterface(MemorySegment pointer) {
      this.pointer = pointer;
      init = method(pointer, 3, FunctionDescriptor.of(JAVA_INT, ADDRESS));
      progressValue = method(pointer, 9, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));
      progressState = method(pointer, 10, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
      overlayIcon = method(pointer, 18, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    }

    private static MethodHandle method(MemorySegment pointer, int slot, FunctionDescriptor descriptor) {
      var table = pointer.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0).reinterpret(21 * ADDRESS.byteSize());
      return Linker.nativeLinker().downcallHandle(table.getAtIndex(ADDRESS, slot), descriptor);
    }

    void init() throws Throwable {
      checkResult("ITaskbarList.HrInit", (int)init.invokeExact(pointer));
    }

    void setProgressValue(MemorySegment window, long completed, long total) {
      try {
        checkResult("ITaskbarList3.SetProgressValue", (int)progressValue.invokeExact(pointer, window, completed, total));
      }
      catch (Throwable error) {
        LOG.warn(error);
      }
    }

    void setProgressState(MemorySegment window, int state) {
      try {
        checkResult("ITaskbarList3.SetProgressState", (int)progressState.invokeExact(pointer, window, state));
      }
      catch (Throwable error) {
        LOG.warn(error);
      }
    }

    void setOverlayIcon(MemorySegment window, MemorySegment icon) {
      try {
        checkResult("ITaskbarList3.SetOverlayIcon", (int)overlayIcon.invokeExact(pointer, window, icon, MemorySegment.NULL));
      }
      catch (Throwable error) {
        LOG.warn(error);
      }
    }
  }
}
