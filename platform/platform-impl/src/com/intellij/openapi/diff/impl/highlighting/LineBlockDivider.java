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

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.util.text.StringUtil;

public abstract class LineBlockDivider {
  public abstract DiffFragment[][] divide(DiffFragment[] lineBlock);

  public static final LineBlockDivider SINGLE_SIDE = new LineBlockDivider() {
    public DiffFragment[][] divide(DiffFragment[] lineBlock) {
      List2D result = new List2D();
      FragmentSide currentSide = null;
      boolean isNewLineLast = true;
      for (int i = 0; i < lineBlock.length; i++) {
        DiffFragment fragment = lineBlock[i];
        if (!fragment.isOneSide()) {
          if (currentSide != null && isNewLineLast) result.newRow();
          isNewLineLast = StringUtil.endsWithChar(fragment.getText1(), '\n') && StringUtil.endsWithChar(fragment.getText2(), '\n');
          currentSide = null;
        } else {
          FragmentSide side = FragmentSide.chooseSide(fragment);
          if (currentSide != side) {
            if (isNewLineLast) {
              result.newRow();
              currentSide = side;
            } else currentSide = null;
          }
          isNewLineLast = StringUtil.endsWithChar(side.getText(fragment), '\n');
        }
        result.add(fragment);
      }
      return result.toArray();
    }
  };
}
