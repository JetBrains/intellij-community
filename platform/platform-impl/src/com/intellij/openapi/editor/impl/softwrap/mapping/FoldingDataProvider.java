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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.FoldRegion;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts information about fold regions received as an array to {@link DataProvider} API.
 *
 * @author Denis Zhdanov
 * @since Aug 31, 2010 12:42:02 PM
 */
public class FoldingDataProvider extends AbstractDataProvider<SoftWrapDataProviderKeys, FoldRegion> {

  private final FoldRegion[] myFoldRegions;
  private int myIndex;

  public FoldingDataProvider(@Nullable FoldRegion[] foldRegions) {
    super(SoftWrapDataProviderKeys.COLLAPSED_FOLDING);
    myFoldRegions = foldRegions;

    if (foldRegions == null) {
      return;
    }
    for (; myIndex < myFoldRegions.length; myIndex++) {
      if (!myFoldRegions[myIndex].isExpanded()) {
        break;
      }
    }
  }

  @Override
  protected FoldRegion doGetData() {
    if (myFoldRegions == null || myIndex >= myFoldRegions.length) {
      return null;
    }
    return myFoldRegions[myIndex];
  }

  @Override
  public boolean next() {
    if (myFoldRegions == null) {
      return false;
    }

    while (++myIndex < myFoldRegions.length) {
      if (!myFoldRegions[myIndex].isExpanded()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void advance(int sortingKey) {
    // We inline binary search here because profiling indicates that as a performance boost.
    int start = myIndex;
    int end = myFoldRegions.length - 1;

    // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
    while (start <= end) {
      int i = (end + start) >>> 1;
      FoldRegion foldRegion = myFoldRegions[i];
      if (foldRegion.getStartOffset() < sortingKey) {
        start = i + 1;
        continue;
      }
      if (foldRegion.getStartOffset() > sortingKey) {
        end = i - 1;
        continue;
      }

      myIndex = i;
      break;
    }
    myIndex = start;
  }

  @Override
  public int getSortingKey() {
    if (myFoldRegions == null || myIndex >= myFoldRegions.length) {
      return 0;
    }
    return myFoldRegions[myIndex].getStartOffset();
  }
}
