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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.TestFileType;

import java.io.IOException;
import java.util.Arrays;

/**
 * Base super class for tests that check various IJ editor functionality on managed document modification.
 * <p/>
 * It's main purpose is to provide utility methods like fold regions addition and setup; typing etc. 
 * 
 * @author Denis Zhdanov
 * @since 11/18/10 7:43 PM
 */
public abstract class AbstractEditorProcessingOnDocumentModificationTest extends LightPlatformCodeInsightTestCase {

  protected void init(String fileText) throws IOException {
    init(fileText, TestFileType.TEXT);
  }
  
  protected void init(String fileText, TestFileType type) throws IOException {
    configureFromFileText(getFileName(type), fileText);
  }

  private String getFileName(TestFileType type) {
    return getTestName(false) + type.getExtension();
  }

  protected static void addFoldRegion(final int startOffset, final int endOffset, final String placeholder) {
    myEditor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        myEditor.getFoldingModel().addFoldRegion(startOffset, endOffset, placeholder);
      }
    });
  }

  protected static void addCollapsedFoldRegion(final int startOffset, final int endOffset, final String placeholder) {
    addFoldRegion(startOffset, endOffset, placeholder);
    toggleFoldRegionState(getFoldRegion(startOffset), false);
  }

  protected static void toggleFoldRegionState(final FoldRegion foldRegion, final boolean expanded) {
    myEditor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        foldRegion.setExpanded(expanded);
      }
    });
  }

  protected static FoldRegion getFoldRegion(int startOffset) {
    FoldRegion[] foldRegions = myEditor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion foldRegion : foldRegions) {
      if (foldRegion.getStartOffset() == startOffset) {
        return foldRegion;
      }
    }
    throw new IllegalArgumentException(String.format(
      "Can't find fold region with start offset %d. Registered fold regions: %s. Document text: '%s'",
      startOffset, Arrays.toString(foldRegions), myEditor.getDocument().getCharsSequence()
    ));
  }
}
