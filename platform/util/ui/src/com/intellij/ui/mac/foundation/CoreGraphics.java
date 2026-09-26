// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import org.jetbrains.annotations.NonNls;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * see <a href="http://developer.apple.com/documentation/Cocoa/Reference/ObjCRuntimeRef/Reference/reference.html">Documentation</a>
 */
public final @NonNls class CoreGraphics {
  private static final MemoryLayout RECT_LAYOUT = MemoryLayout.structLayout(JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE);

  private CoreGraphics() { }

  public static ID cgWindowListCreateImage(CGRect screenBounds, int windowOption, int windowID, int imageOption) {
    var image = (MemorySegment)FoundationNative.call("CGWindowListCreateImage",
                                                     FunctionDescriptor.of(ADDRESS, RECT_LAYOUT, JAVA_INT, JAVA_INT, JAVA_INT),
                                                     screenBounds, windowOption, windowID, imageOption);
    return new ID(image.address());
  }

  public static final class CGRect {
    public CGPoint origin;
    public CGSize size;

    public CGRect(double x, double y, double w, double h) {
      origin = new CGPoint(x, y);
      size = new CGSize(w, h);
    }
  }

  public static final class CGPoint {
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

  public static final class CGSize {
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

  public static final class CGFloat extends Number {
    private final double value;

    @SuppressWarnings("UnusedDeclaration")
    public CGFloat() {
      this(0);
    }

    public CGFloat(double d) {
      value = d;
    }

    @Override
    public int intValue() {
      return (int)value;
    }

    @Override
    public long longValue() {
      return (long)value;
    }

    @Override
    public float floatValue() {
      return (float)value;
    }

    @Override
    public double doubleValue() {
      return value;
    }
  }
}
