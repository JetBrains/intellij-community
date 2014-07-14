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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.NotNull;

/**
 * Caches information about number of logical columns inside the collapsed single line folding.
 */
class FoldingData {
  private final int myWidthInColumns;
  private final FoldRegion myFoldRegion;

  FoldingData(@NotNull FoldRegion foldRegion, int widthInColumns) {
    myFoldRegion = foldRegion;
    myWidthInColumns = widthInColumns;
  }

  public int getCollapsedSymbolsWidthInColumns() {
    return myWidthInColumns;
  }

  @NotNull
  public FoldRegion getFoldRegion() {
    return myFoldRegion;
  }

  @Override
  public String toString() {
    return "width in columns: " + myWidthInColumns + ", fold region: " + myFoldRegion;
  }
}
