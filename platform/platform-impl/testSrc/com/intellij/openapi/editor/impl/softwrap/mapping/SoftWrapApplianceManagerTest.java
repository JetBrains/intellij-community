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
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.SoftWrapModelEx;
import com.intellij.openapi.editor.impl.FoldRegionImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.awt.*;
import java.io.IOException;

/**
 * @author Denis Zhdanov
 * @since 09/16/2010
 */
public class SoftWrapApplianceManagerTest extends LightPlatformCodeInsightTestCase {

  private static final String PATH = "/codeInsight/softwrap/";

  @Override
  protected void tearDown() throws Exception {
    myEditor.getSettings().setUseSoftWraps(false);
    super.tearDown();
  }

  public void testSoftWrapAdditionOnTyping() throws Exception {
    init(800);

    int offset = myEditor.getDocument().getTextLength() + 1;
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type(" thisisalongtokenthatisnotexpectedtobebrokenintopartsduringsoftwrapping");
    assertNotNull(myEditor.getSoftWrapModel().getSoftWrap(offset));
  }

  public void testLongLineOfIdSymbolsIsNotSoftWrapped() throws Exception {
    init(100);
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());
    type('1');
    assertTrue(getSoftWrapModel().getRegisteredSoftWraps().isEmpty());

    int offset = myEditor.getDocument().getText().indexOf("\n");
    type(" test");
    assertEquals(1, getSoftWrapModel().getRegisteredSoftWraps().size());
    assertNotNull(getSoftWrapModel().getSoftWrap(offset));
  }

  public void testFoldRegionCollapsing() throws Exception {
    String text =
      "class Test {\n" +
      "    public void foo() {\n" +
      "        System.out.println(\"test\");\n" +
      "    }\n" +
      "}";

    init(300, text);
    final FoldingModel foldingModel = myEditor.getFoldingModel();
    assertEmpty(foldingModel.getAllFoldRegions());

    final int startOffset = text.indexOf('{');
    final int endOffset = text.indexOf('}') + 1;

    VisualPosition foldStartPosition = myEditor.offsetToVisualPosition(startOffset);

    foldingModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        foldingModel.addFoldRegion(startOffset, endOffset, "...");
      }
    });

    final FoldRegion foldRegion = foldingModel.getAllFoldRegions()[0];
    assertNotNull(foldRegion);
    assertTrue(foldRegion.isExpanded());
    foldingModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        foldRegion.setExpanded(false);
      }
    });

    // Expecting that all offsets that belong to collapsed fold region point to the region's start.
    assertEquals(foldStartPosition, myEditor.offsetToVisualPosition(startOffset + 5));
  }

  private void init(final int visibleWidth) throws Exception {
    configureByFile(PATH + getFileName());
    initCommon(visibleWidth);
  }

  private void init(int visibleWidth, String fileText) throws IOException {
    configureFromFileText(getFileName(), fileText);
    initCommon(visibleWidth);
  }

  private String getFileName() {
    return getTestName(false) + ".txt";
  }

  private static void initCommon(final int visibleWidth) {
    myEditor.getSettings().setUseSoftWraps(true);
    SoftWrapModelImpl model = (SoftWrapModelImpl)myEditor.getSoftWrapModel();
    model.refreshSettings();

    SoftWrapApplianceManager applianceManager = model.getApplianceManager();
    applianceManager.setWidthProvider(new SoftWrapApplianceManager.VisibleAreaWidthProvider() {
      @Override
      public int getVisibleAreaWidth() {
        return visibleWidth;
      }
    });
    applianceManager.registerSoftWrapIfNecessary(new Rectangle(visibleWidth, visibleWidth * 2), 0);
  }

  private static SoftWrapModelEx getSoftWrapModel() {
    return (SoftWrapModelEx)myEditor.getSoftWrapModel();
  }
}
