// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

final class MergeFragment {

  @NotNull private final TextRange myLeft;
  @NotNull private final TextRange myBase;
  @NotNull private final TextRange myRight;

  MergeFragment(@NotNull TextRange left, @NotNull TextRange base, @NotNull TextRange right) {
    myLeft = left;
    myBase = base;
    myRight = right;
  }

  @NotNull
  TextRange getLeft() {
    return myLeft;
  }

  @NotNull
  TextRange getBase() {
    return myBase;
  }

  @NotNull
  TextRange getRight() {
    return myRight;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MergeFragment fragment = (MergeFragment)o;

    if (!myBase.equals(fragment.myBase)) return false;
    if (!myLeft.equals(fragment.myLeft)) return false;
    if (!myRight.equals(fragment.myRight)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = myLeft.hashCode();
    result = 31 * result + myBase.hashCode();
    result = 31 * result + myRight.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "<" + myLeft.toString() + ", " + myBase.toString() + ", " + myRight.toString() + ">";
  }
}
