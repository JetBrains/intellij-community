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
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.util.ArrayUtil;

import java.util.ArrayList;

class BufferedStringList {
  private final ArrayList<String> myStrings = new ArrayList<String>();
  private final StringBuffer myLast = new StringBuffer();

  public void add(String string) {
    flushLast();
    myStrings.add(string);
  }

  public void appendToLast(String string) {
    myLast.append(string);
  }

  public void flushLast() {
    if (myLast.length() > 0) {
      myStrings.add(myLast.toString());
      myLast.setLength(0);
    }
  }

  public String[] toArray() {
    flushLast();
    return ArrayUtil.toStringArray(myStrings);
  }
}
