// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.intellij.jna.JnaLoader;
import com.sun.jna.*;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;

/**
 * see <a href="http://developer.apple.com/documentation/Cocoa/Reference/ObjCRuntimeRef/Reference/reference.html">Documentation</a>
 */
@NonNls
public final class CoreGraphics {
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

  @Structure.FieldOrder({"origin", "size"})
  public static class CGRect extends Structure implements Structure.ByValue {
    public CGPoint origin;
    public CGSize size;

    public CGRect(double x, double y, double w, double h) {
      origin = new CGPoint(x, y);
      size = new CGSize(w, h);
    }
  }

  @Structure.FieldOrder({"x", "y"})
  public static class CGPoint extends Structure implements Structure.ByValue {
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
  public static class CGSize extends Structure implements Structure.ByValue {
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

  public static class CGFloat implements NativeMapped {
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
      switch (Native.LONG_SIZE) {
        case 4:
          return new CGFloat((Float)o);
        case 8:
          return new CGFloat((Double)o);
      }
      throw new IllegalStateException();
    }

    @Override
    public Object toNative() {
      switch (Native.LONG_SIZE) {
        case 4:
          return (float)value;
        case 8:
          return value;
      }
      throw new IllegalStateException();
    }

    @Override
    public Class<?> nativeType() {
      switch (Native.LONG_SIZE) {
        case 4:
          return Float.class;
        case 8:
          return Double.class;
      }
      throw new IllegalStateException();
    }
  }
}
