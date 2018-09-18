// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.highlighting;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.util.text.StringUtil;

public abstract class LineBlockDivider {
  public abstract DiffFragment[][] divide(DiffFragment[] lineBlock);

  public static final LineBlockDivider SINGLE_SIDE = new LineBlockDivider() {
    @Override
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
