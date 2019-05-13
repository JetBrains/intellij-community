/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.actions;

public class ChangedLines {
  public final int from;
  public final int to;

  ChangedLines(int from, int to) {
    this.from = from;
    this.to = to;
  }

  @Override
  public String toString() {
    return "(" + from + ", " + to + ")";
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ChangedLines) {
      ChangedLines line = (ChangedLines)obj;
      return from == line.from && to == line.to;
    }

    return false;
  }
}
