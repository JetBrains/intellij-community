/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.splitter;

class LinearTransformation implements Transformation {
  private final int myK;
  private final int myX0;

  public LinearTransformation(int offset, int lineHeight) {
    myK = lineHeight;
    myX0 = -offset;
  }

  public int transform(int line) {
    return linear(line, myX0, myK);
  }

  private static int linear(int x, int x0, int k) {
    return k*x + x0;
  }

  public static int oneToOne(int x, int x0, Interval range) {
    if (range.getLength() == 0) return range.getStart();
    return range.getStart() + Math.min(x - x0, range.getLength() - 1);
  }
}
