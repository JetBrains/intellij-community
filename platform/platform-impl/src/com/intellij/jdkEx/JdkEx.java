// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkEx;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.MethodInvocator;
import com.intellij.util.lang.JavaVersion;
import io.netty.util.internal.SystemPropertyUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import java.awt.*;
import java.util.List;

/**
 * Provides extensions for OpenJDK API, implemented in JetBrains JDK.
 * For OpenJDK defaults to some meaningful results where applicable or otherwise throws runtime exception.
 *
 * WARNING: For internal usage only.
 *
 * @author tav
 */
public final class JdkEx {
  @SuppressWarnings("unused")
  @NotNull
  public static InputEventEx getInputEventEx() {
    if (SystemInfo.isJetBrainsJvm) {
      return new JBInputEventEx();
    }
    return new DefInputEventEx();
  }

  public static DisplayModeEx getDisplayModeEx() {
    if (SystemInfo.isJetBrainsJvm) {
      return new JBDisplayModeEx();
    }
    return new DefDisplayModeEx();
  }

  // CUSTOM DECOR SUPPORT {{

  public static boolean isCustomDecorationSupported() {
    if (SystemInfo.isJetBrainsJvm && SystemInfo.isWin10OrNewer) {
      return MyCustomDecorMethods.SET_HAS_CUSTOM_DECORATION.isAvailable();
    }
    return false;
  }

  public static void setHasCustomDecoration(@NotNull Window window) {
    if (!isCustomDecorationSupported()) return;
    MyCustomDecorMethods.SET_HAS_CUSTOM_DECORATION.invoke(window);
  }

  public static void setCustomDecorationHitTestSpots(@NotNull Window window, @NotNull List<Rectangle> spots) {
    if (!isCustomDecorationSupported()) return;
    MyCustomDecorMethods.SET_CUSTOM_DECORATION_HITTEST_SPOTS.invoke(AWTAccessor.getComponentAccessor().getPeer(window), spots);
  }

  public static void setCustomDecorationTitleBarHeight(@NotNull Window window, int height) {
    if (!isCustomDecorationSupported()) return;
    MyCustomDecorMethods.SET_CUSTOM_DECORATION_TITLEBAR_HEIGHT.invoke(AWTAccessor.getComponentAccessor().getPeer(window), height);
  }

  // lazy init
  private static final class MyCustomDecorMethods {
    public static final MyMethod SET_HAS_CUSTOM_DECORATION =
      MyMethod.create(Window.class, "setHasCustomDecoration");
    public static final MyMethod SET_CUSTOM_DECORATION_HITTEST_SPOTS =
      MyMethod.create("sun.awt.windows.WWindowPeer", "setCustomDecorationHitTestSpots", List.class);
    public static final MyMethod SET_CUSTOM_DECORATION_TITLEBAR_HEIGHT =
      MyMethod.create("sun.awt.windows.WWindowPeer","setCustomDecorationTitleBarHeight", int.class);
  }

  // }} CUSTOM DECOR SUPPORT

  private static final class MyMethod {
    private static final MyMethod EMPTY_INSTANCE = new MyMethod(null);

    @Nullable MethodInvocator myInvocator;

    public static MyMethod create(@NotNull String className, @NotNull String methodName, Class<?>... parameterTypes) {
      try {
        return create(Class.forName(className), methodName, parameterTypes);
      }
      catch (ClassNotFoundException ignore) {
      }
      return EMPTY_INSTANCE;
    }

    public static MyMethod create(@NotNull Class<?> cls, @NotNull String methodName, Class<?>... parameterTypes) {
      return new MyMethod(new MethodInvocator(false, cls, methodName, parameterTypes));
    }

    private MyMethod(@Nullable MethodInvocator invocator) {
      this.myInvocator = invocator;
    }

    public boolean isAvailable() {
      return myInvocator != null && myInvocator.isAvailable();
    }

    @Nullable
    public Object invoke(Object object, Object... arguments) {
      if (isAvailable()) {
        //noinspection ConstantConditions
        return myInvocator.invoke(object, arguments);
      }
      return null;
    }
  }

  public static void setIgnoreMouseEvents(@NotNull Window window, boolean ignoreMouseEvents) {
    if (SystemInfo.isJetBrainsJvm && (SystemInfo.isMac || SystemInfo.isWindows)) {
      window.setEnabled(false);
      try {
        MethodInvocator invocator =
          new MethodInvocator(false, Class.forName("java.awt.Window"), "setIgnoreMouseEvents", boolean.class);
        if (invocator.isAvailable()) {
          invocator.invoke(window, ignoreMouseEvents);
        }
      }
      catch (ClassNotFoundException ignore) {
      }
    }
  }

  private static MethodInvocator mySetTabbingMode;

  @Nullable
  private static MethodInvocator getTabbingModeInvocator() {
    if (!SystemInfo.isJetBrainsJvm || !SystemInfo.isMacOSBigSur || !Registry.is("ide.mac.bigsur.window.with.tabs.enabled", true)) {
      return null;
    }
    if (mySetTabbingMode == null) {
      try {
        mySetTabbingMode = new MethodInvocator(false, Class.forName("java.awt.Window"), "setTabbingMode");
        if (mySetTabbingMode.isAvailable()) {
          return mySetTabbingMode;
        }
      }
      catch (ClassNotFoundException ignore) {
      }
      return null;
    }
    return mySetTabbingMode.isAvailable() ? mySetTabbingMode : null;
  }

  public static boolean isTabbingModeAvailable() {
    return getTabbingModeInvocator() != null;
  }

  public static boolean setTabbingMode(@NotNull Window window, @Nullable Runnable moveTabToNewWindowCallback) {
    MethodInvocator invocator = getTabbingModeInvocator();
    if (invocator != null) {
      invocator.invoke(window);
      if (moveTabToNewWindowCallback != null) {
        try {
          new MethodInvocator(false, Class.forName("java.awt.Window"), "setMoveTabToNewWindowCallback", Runnable.class)
            .invoke(window, moveTabToNewWindowCallback);
        }
        catch (ClassNotFoundException ignore) {
        }
      }
      return true;
    }
    return false;
  }

  private static MethodInvocator ourSetFileDialogLocalizationStringsMethod;

  @Nullable
  private static MethodInvocator getFileDialogLocalizationStringsMethod() {
    if (!SystemInfo.isJetBrainsJvm || !SystemInfo.isWindows || !Registry.is("ide.win.file.chooser.native", false) || !SystemPropertyUtil.getBoolean("sun.awt.windows.useCommonItemDialog", false)) {
      return null;
    }
    if (ourSetFileDialogLocalizationStringsMethod == null) {
      ourSetFileDialogLocalizationStringsMethod = new MethodInvocator(
        false,
        FileDialog.class,
        "setLocalizationStrings",
        String.class,
        String.class);
      if (ourSetFileDialogLocalizationStringsMethod.isAvailable()) {
        return ourSetFileDialogLocalizationStringsMethod;
      }
      return null;
    }

    return ourSetFileDialogLocalizationStringsMethod.isAvailable() ? ourSetFileDialogLocalizationStringsMethod : null;
  }

  public static boolean trySetCommonFileDialogLocalization(@NotNull FileDialog fileDialog,
                                                           @Nullable @Nls String openButtonText,
                                                           @Nullable @Nls String selectFolderButtonText) {
    MethodInvocator setLocalizationStrings = getFileDialogLocalizationStringsMethod();
    if (setLocalizationStrings == null) return false;
    try {
      setLocalizationStrings.invoke(fileDialog, openButtonText, selectFolderButtonText);
      return true;
    }
    catch (Throwable t) {
      Logger.getInstance(JdkEx.class).error(t);
      return false;
    }
  }

  private static MethodInvocator ourSetFolderPickerModeMethod;

  @Nullable
  private static MethodInvocator getSetFolderPickerModeMethod() {
    if (!SystemInfo.isJetBrainsJvm || !SystemInfo.isWindows || !Registry.is("ide.win.file.chooser.native", false) || !SystemPropertyUtil.getBoolean("sun.awt.windows.useCommonItemDialog", false)) {
      return null;
    }
    if (ourSetFolderPickerModeMethod == null) {
      ourSetFolderPickerModeMethod = new MethodInvocator(
        false,
        FileDialog.class,
        "setFolderPickerMode",
        boolean.class);
      if (ourSetFolderPickerModeMethod.isAvailable()) {
        return ourSetFolderPickerModeMethod;
      }
      return null;
    }

    return ourSetFolderPickerModeMethod.isAvailable() ? ourSetFolderPickerModeMethod : null;
  }

  public static boolean trySetFolderPickerMode(@NotNull FileDialog fileDialog, boolean folderPickerMode) {
    MethodInvocator setFolderPickerMode = getSetFolderPickerModeMethod();
    if (setFolderPickerMode == null) return false;
    try {
      setFolderPickerMode.invoke(fileDialog, folderPickerMode);
      return true;
    }
    catch (Throwable t) {
      Logger.getInstance(JdkEx.class).error(t);
      return false;
    }
  }

  private static MethodInvocator ourSetFileExclusivePickerModeMethod;

  @Nullable
  private static MethodInvocator getSetFileExclusivePickerModeMethod() {
    if (!SystemInfo.isJetBrainsJvm || !SystemInfo.isWindows || !Registry.is("ide.win.file.chooser.native", false) || !SystemPropertyUtil.getBoolean("sun.awt.windows.useCommonItemDialog", false)) {
      return null;
    }
    if (ourSetFileExclusivePickerModeMethod == null) {
      ourSetFileExclusivePickerModeMethod = new MethodInvocator(
        false,
        FileDialog.class,
        "setFileExclusivePickerMode",
        boolean.class);
      if (ourSetFileExclusivePickerModeMethod.isAvailable()) {
        return ourSetFileExclusivePickerModeMethod;
      }
      return null;
    }

    return ourSetFileExclusivePickerModeMethod.isAvailable() ? ourSetFileExclusivePickerModeMethod : null;
  }

  public static boolean trySetFileExclusivePickerMode(@NotNull FileDialog fileDialog, boolean fileExclusivePickerMode) {
    MethodInvocator setFileExclusivePickerMode = getSetFileExclusivePickerModeMethod();
    if (setFileExclusivePickerMode == null) return false;
    try {
      setFileExclusivePickerMode.invoke(fileDialog, fileExclusivePickerMode);
      return true;
    }
    catch (Throwable t) {
      Logger.getInstance(JdkEx.class).error(t);
      return false;
    }
  }

  public static boolean trySetFileDialogCanChooseModes(@NotNull FileDialog fileDialog, boolean chooseDirectories, boolean chooseFiles) {
    // TODO: need API from JBR team for direct sets all flags into $fileDialog$
    if (SystemInfo.isJetBrainsJvm && SystemInfo.isMac && JavaVersion.current().isAtLeast(17)) {
      //System.setProperty("apple.awt.fileDialogForDirectories", Boolean.toString(chooseDirectories));
      //System.setProperty("apple.awt.fileDialogForFiles", Boolean.toString(chooseFiles));
      System.setProperty("apple.awt.fileDialogForDirectories", Boolean.toString(chooseDirectories && !chooseFiles));
      return true;
    }
    return false;
  }
}
