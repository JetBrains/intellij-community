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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents one fragment in the 3-way merge, i.e. an insertion, a deletion, a modification or a conflict.
 * Holds information about the {@link TextRange TextRanges} of changed fragments.<br/>
 * One and only one left or right fragment is null for all cases, except for the conflict, when both are not null.
 */
final class MergeFragment {

  @Nullable private final TextRange myLeft;
  @NotNull private final TextRange myBase;
  @Nullable private final TextRange myRight;

  MergeFragment(@Nullable TextRange left, @NotNull TextRange base, @Nullable TextRange right) {
    myLeft = left;
    myBase = base;
    myRight = right;
  }

  /**
   * Creates a non-conflicting MergeFragment, given one of TextRanges and the FragmentSide to identify which range is it: right or left.
   */
  @NotNull
  static MergeFragment notConflict(@NotNull TextRange baseChange, @NotNull TextRange versionChange, @NotNull FragmentSide versionSide) {
    TextRange left;
    TextRange right;
    if (versionSide == FragmentSide.SIDE1) {
      left = versionChange;
      right = null;
    }
    else {
      left = null;
      right = versionChange;
    }
    return new MergeFragment(left, baseChange, right);
  }

  @Nullable
  TextRange getLeft() {
    return myLeft;
  }

  @NotNull
  TextRange getBase() {
    return myBase;
  }

  @Nullable
  TextRange getRight() {
    return myRight;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MergeFragment fragment = (MergeFragment)o;

    // suppressing unnecessary check for null, because myBase may occasionally become nullable
    //noinspection ConstantConditions
    if (myBase != null ? !myBase.equals(fragment.myBase) : fragment.myBase != null) return false;
    if (myLeft != null ? !myLeft.equals(fragment.myLeft) : fragment.myLeft != null) return false;
    if (myRight != null ? !myRight.equals(fragment.myRight) : fragment.myRight != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLeft != null ? myLeft.hashCode() : 0;
    // suppressing unnecessary check for null, because myBase may occasionally become nullable
    // noinspection ConstantConditions
    result = 31 * result + (myBase != null ? myBase.hashCode() : 0);
    result = 31 * result + (myRight != null ? myRight.hashCode() : 0);
    return result;
  }

  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    buffer.append(range2String(myLeft)).append(", ");
    buffer.append(range2String(myBase)).append(", ");
    buffer.append(range2String(myRight));
    buffer.append(">");
    return buffer.toString();
  }

  @NotNull
  private static String range2String(@Nullable TextRange left) {
    return left != null ? left.toString() : "-----";
  }

}
