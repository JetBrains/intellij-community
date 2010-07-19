/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since Jun 30, 2010 1:55:53 PM
 */
public class MockFoldRegion extends UserDataHolderBase implements FoldRegion {

  private final int myStartOffset;
  private final int myEndOffset;

  public MockFoldRegion(int startOffset, int endOffset) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public boolean isExpanded() {
    return false;
  }

  @Override
  public void setExpanded(boolean expanded) {
  }

  @NotNull
  @Override
  public String getPlaceholderText() {
    return "...";
  }

  @Override
  public Editor getEditor() {
    throw new UnsupportedOperationException("FoldRegion.getEditor() is not implemented yet at " + getClass());
  }

  @Override
  public FoldingGroup getGroup() {
    throw new UnsupportedOperationException("FoldRegion.getGroup() is not implemented yet at " + getClass());
  }

  @NotNull
  @Override
  public Document getDocument() {
    throw new UnsupportedOperationException("FoldRegion.getDocument() is not implemented yet at " + getClass());
  }

  @Override
  public int getStartOffset() {
    return myStartOffset;
  }

  @Override
  public int getEndOffset() {
    return myEndOffset;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void setGreedyToLeft(boolean greedy) {
    throw new UnsupportedOperationException("FoldRegion.setGreedyToLeft() is not implemented yet at " + getClass());
  }

  @Override
  public void setGreedyToRight(boolean greedy) {
    throw new UnsupportedOperationException("FoldRegion.setGreedyToRight() is not implemented yet at " + getClass());
  }

  @Override
  public boolean isGreedyToRight() {
    throw new UnsupportedOperationException("FoldRegion.isGreedyToRight() is not implemented yet at " + getClass());
  }

  @Override
  public boolean isGreedyToLeft() {
    throw new UnsupportedOperationException("FoldRegion.isGreedyToLeft() is not implemented yet at " + getClass());
  }

  @Override
  public String toString() {
    return myStartOffset + "-" + myEndOffset;
  }
}
