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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

/**
* @author Kirill Likhodedov
*/
public abstract class ChangeSide {

  public int getStart() {
    return getRange().getStartOffset();
  }

  public int getStartLine() {
    return DocumentUtil.getStartLine(getRange());
  }

  @NotNull
  public CharSequence getText() {
    DiffRangeMarker range = getRange();
    return range.getDocument().getCharsSequence().subSequence(range.getStartOffset(), range.getEndOffset());
  }

  public int getEndLine() {
    return DocumentUtil.getEndLine(getRange());
  }

  @NotNull
  public abstract DiffRangeMarker getRange();

  @NotNull
  public abstract ChangeHighlighterHolder getHighlighterHolder();

  public boolean contains(int offset) {
    return getStart() <= offset && offset < getEnd();
  }

  public int getEnd() {
    return getRange().getEndOffset();
  }
}
