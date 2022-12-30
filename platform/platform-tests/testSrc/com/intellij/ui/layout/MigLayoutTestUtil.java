// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.layout;

import net.miginfocom.layout.Grid;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

final class MigLayoutTestUtil {
  @SuppressWarnings("unchecked")
  static List<int[]> getRectangles(@NotNull Grid grid) throws NoSuchFieldException, IllegalAccessException {
    Field field = Grid.class.getDeclaredField("debugRects");
    field.setAccessible(true);

    return (List<int[]>)field.get(grid);
  }
}
