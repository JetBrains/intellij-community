// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.intellij.jna.JnaLoader;
import com.sun.jna.FromNativeContext;
import com.sun.jna.Native;
import com.sun.jna.NativeMapped;
import com.sun.jna.Structure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;

/**
 * see <a href="http://developer.apple.com/documentation/Cocoa/Reference/ObjCRuntimeRef/Reference/reference.html">Documentation</a>
 */
public final @NonNls class CoreGraphics {
  private static final CoreGraphicsLibrary myCoreGraphicsLibrary;

  static {
    assert JnaLoader.isLoaded() : "JNA library is not available";
    myCoreGraphicsLibrary = Native.load("CoreGraphics", CoreGraphicsLibrary.class, Collections.singletonMap("jna.encoding", "UTF8"));
  }

  private CoreGraphics() { }

  public static ID cgWindowListCreateImage(CGRect screenBounds, int windowOption, ID windowID, int imageOption) {
    return myCoreGraphicsLibrary.CGWindowListCreateImage(screenBounds,
                                                         windowOption,
                                                         windowID,
                                                         imageOption);
  }

  /**
   * Whether this process may record the screen. When it may not, a capture still succeeds but shows only the
   * desktop background, so a caller that would otherwise hand the user an empty picture can warn about it.
   * <p>
   * Advisory only: once this has returned {@code false} it keeps returning {@code false} for the rest of the
   * process, even after the user allows the grant, while the capture APIs commonly start working immediately.
   * Do not gate a capture on it.
   */
  @ApiStatus.Internal
  public static boolean preflightScreenCaptureAccess() {
    return myCoreGraphicsLibrary.CGPreflightScreenCaptureAccess();
  }

  /**
   * Asks for screen-recording access, which shows the system dialog the first time and does nothing on a later
   * call. Returns immediately — the dialog belongs to another process — with the same value
   * {@link #preflightScreenCaptureAccess()} would return, so it says nothing about what the user will choose.
   */
  @ApiStatus.Internal
  public static boolean requestScreenCaptureAccess() {
    return myCoreGraphicsLibrary.CGRequestScreenCaptureAccess();
  }

  @Structure.FieldOrder({"origin", "size"})
  public static final class CGRect extends Structure implements Structure.ByValue {
    public CGPoint origin;
    public CGSize size;

    public CGRect(double x, double y, double w, double h) {
      origin = new CGPoint(x, y);
      size = new CGSize(w, h);
    }
  }

  @Structure.FieldOrder({"x", "y"})
  public static final class CGPoint extends Structure implements Structure.ByValue {
    public CGFloat x;
    public CGFloat y;

    @SuppressWarnings("UnusedDeclaration")
    public CGPoint() {
      this(0, 0);
    }

    public CGPoint(double x, double y) {
      this.x = new CGFloat(x);
      this.y = new CGFloat(y);
    }
  }

  @Structure.FieldOrder({"width", "height"})
  public static final class CGSize extends Structure implements Structure.ByValue {
    public CGFloat width;
    public CGFloat height;

    @SuppressWarnings("UnusedDeclaration")
    public CGSize() {
      this(0, 0);
    }

    public CGSize(double width, double height) {
      this.width = new CGFloat(width);
      this.height = new CGFloat(height);
    }
  }

  public static final class CGFloat implements NativeMapped {
    private final double value;

    @SuppressWarnings("UnusedDeclaration")
    public CGFloat() {
      this(0);
    }

    public CGFloat(double d) {
      value = d;
    }

    @Override
    public Object fromNative(Object o, FromNativeContext fromNativeContext) {
      return switch (Native.LONG_SIZE) {
        case 4 -> new CGFloat((Float)o);
        case 8 -> new CGFloat((Double)o);
        default -> throw new IllegalStateException();
      };
    }

    @Override
    public Object toNative() {
      return switch (Native.LONG_SIZE) {
        case 4 -> (float)value;
        case 8 -> value;
        default -> throw new IllegalStateException();
      };
    }

    @Override
    public Class<?> nativeType() {
      return switch (Native.LONG_SIZE) {
        case 4 -> Float.class;
        case 8 -> Double.class;
        default -> throw new IllegalStateException();
      };
    }
  }
}
