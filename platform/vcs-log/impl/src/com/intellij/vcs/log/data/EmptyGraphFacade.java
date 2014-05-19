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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.graph.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class EmptyGraphFacade implements GraphFacade {

  public static final BufferedImage EMPTY_IMAGE = UIUtil.createImage(1, 1, Transparency.TRANSLUCENT);

  @NotNull
  @Override
  public PaintInfo paint(int visibleRow) {
    return new PaintInfo() {
      @NotNull
      @Override
      public Image getImage() {
        return EMPTY_IMAGE;
      }

      @Override
      public int getWidth() {
        return 0;
      }
    };
  }

  @Nullable
  @Override
  public GraphAnswer performAction(@NotNull GraphAction action) {
    return null;
  }

  @NotNull
  @Override
  public List<GraphCommit<Integer>> getAllCommits() {
    return Collections.emptyList();
  }

  @Override
  public int getCommitAtRow(int visibleRow) {
    return -1;
  }

  @Override
  public int getVisibleCommitCount() {
    return 0;
  }

  @Override
  public void setVisibleBranches(@Nullable Collection<Integer> heads) {
  }

  @Override
  public void setFilter(@Nullable Condition<Integer> visibilityPredicate) {
  }

  @NotNull
  @Override
  public GraphInfoProvider getInfoProvider() {
    return new GraphInfoProvider() {
      @NotNull
      @Override
      public Set<Integer> getContainingBranches(int visibleRow) {
        return Collections.emptySet();
      }

      @NotNull
      @Override
      public RowInfo getRowInfo(int visibleRow) {
        return new RowInfo() {
          @Override
          public int getOneOfHeads() {
            return -1;
          }
        };
      }

      @Override
      public boolean areLongEdgesHidden() {
        return false;
      }
    };
  }
}
