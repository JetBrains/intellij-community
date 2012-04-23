/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model.agrid;

import com.intellij.designer.model.RadComponent;
import com.intellij.util.ArrayUtil;

/**
 * @author Alexander Lobas
 */
public class GridInfo {
  public int width;
  public int height;

  public int[] hLines = ArrayUtil.EMPTY_INT_ARRAY;
  public int[] vLines = ArrayUtil.EMPTY_INT_ARRAY;
  public boolean[] emptyColumns = ArrayUtil.EMPTY_BOOLEAN_ARRAY;

  public RadComponent[][] components;
  public int lastRow = -1;
  public int lastColumn = -1;
}