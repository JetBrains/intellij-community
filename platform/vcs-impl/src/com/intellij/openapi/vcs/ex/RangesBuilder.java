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
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.editor.Document;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

/**
 * author: lesya
 */

public class RangesBuilder {
  private List<Range> myRanges;

  public RangesBuilder(@NotNull Document current, @NotNull Document vcs) throws FilesTooBigForDiffException {
    this(new DocumentWrapper(current).getLines(), new DocumentWrapper(vcs).getLines(), 0, 0);
  }

  public RangesBuilder(@NotNull List<String> current, @NotNull List<String> vcs, int shift, int vcsShift) throws FilesTooBigForDiffException {
    myRanges = new LinkedList<Range>();

    Diff.Change ch = Diff.buildChanges(ArrayUtil.toStringArray(vcs), ArrayUtil.toStringArray(current));

    while (ch != null) {
      Range range = Range.createOn(ch, shift, vcsShift);
      myRanges.add(range);
      ch = ch.link;
    }
  }

  public List<Range> getRanges() {
    return myRanges;
  }

}
