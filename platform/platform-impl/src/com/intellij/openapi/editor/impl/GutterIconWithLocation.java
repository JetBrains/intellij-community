// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.GutterMark;

import java.awt.*;

public class GutterIconWithLocation {
    private final GutterMark mark;
    private final int line;
    private final Point location;

    public GutterIconWithLocation(GutterMark mark, int line, Point location) {
      this.mark = mark;
      this.line = line;
      this.location = location;
    }

    public GutterMark getMark() {
      return mark;
    }

    public int getLine() {
      return line;
    }

    public Point getLocation() {
      return location;
    }
  }