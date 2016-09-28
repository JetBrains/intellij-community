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
package com.intellij.vcs.log.paint;

public class PaintParameters {

  private static final int WIDTH_NODE = 15;
  private static final int CIRCLE_RADIUS = 4;
  private static final int SELECT_CIRCLE_RADIUS = 5;
  private static final float THICK_LINE = 1.5f;
  private static final float SELECT_THICK_LINE = 2.5f;

  public static final int ROW_HEIGHT = 22;
  public static final int LABEL_PADDING = 3;

  public static int getNodeWidth(int rowHeight) {
    return WIDTH_NODE * rowHeight / ROW_HEIGHT;
  }

  public static float getLineThickness(int rowHeight) {
    return THICK_LINE * rowHeight / ROW_HEIGHT;
  }

  public static float getSelectedLineThickness(int rowHeight) {
    return SELECT_THICK_LINE * rowHeight / ROW_HEIGHT;
  }

  public static int getSelectedCircleRadius(int rowHeight) {
    return SELECT_CIRCLE_RADIUS * rowHeight / ROW_HEIGHT;
  }

  public static int getCircleRadius(int rowHeight) {
    return CIRCLE_RADIUS * rowHeight / ROW_HEIGHT;
  }
}
