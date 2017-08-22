/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.fragments;

import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class LineBlock {
  private final int myStartingLine1;
  private final int myModifiedLines1;
  private final int myStartingLine2;
  private final int myModifiedLines2;
  private TextDiffTypeEnum myType;

  public LineBlock(int startingLine1, int modifiedLines1, int startingLine2, int modifiedLines2, TextDiffTypeEnum blockType) {
    myStartingLine1 = startingLine1;
    myModifiedLines1 = modifiedLines1;
    myStartingLine2 = startingLine2;
    myModifiedLines2 = modifiedLines2;
    myType = blockType;
  }

  public int getModifiedLines1() {
    return myModifiedLines1;
  }

  public int getStartingLine1() {
    return myStartingLine1;
  }

  public int getStartingLine2() {
    return myStartingLine2;
  }

  public int getModifiedLines2() {
    return myModifiedLines2;
  }

  protected int getEndLine1() {
    return myStartingLine1 + myModifiedLines1;
  }

  protected int getEndLine2() {
    return myStartingLine2 + myModifiedLines2;
  }

  public static final Comparator<LineBlock> COMPARATOR = new Comparator<LineBlock>() {
    @Override
    public int compare(LineBlock block1, LineBlock block2) {
      return Comparing.compare(block1.getStartingLine1(), block2.getStartingLine1());
    }
  };

  public TextDiffTypeEnum getType() {
    return myType;
  }

  protected void setType(@NotNull TextDiffTypeEnum type) {
    myType = type;
  }

}
