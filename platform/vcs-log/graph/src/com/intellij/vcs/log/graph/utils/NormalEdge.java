/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.utils;

public class NormalEdge {
  public final int up;
  public final int down;

  public static NormalEdge create(int up, int down) {
    return new NormalEdge(up, down);
  }

  public NormalEdge(int up, int down) {
    this.up = up;
    this.down = down;
  }

  public int getUp() {
    return up;
  }

  public int getDown() {
    return down;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NormalEdge that = (NormalEdge)o;

    if (up != that.up) return false;
    if (down != that.down) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = up;
    result = 31 * result + down;
    return result;
  }
}
