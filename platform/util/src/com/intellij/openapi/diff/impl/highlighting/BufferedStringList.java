// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

class BufferedStringList {
  private final ArrayList<String> myStrings = new ArrayList<>();
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

  @NotNull
  public String[] toArray() {
    flushLast();
    return ArrayUtilRt.toStringArray(myStrings);
  }
}
