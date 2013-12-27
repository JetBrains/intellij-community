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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.impl.AbstractEditorProcessingOnDocumentModificationTest;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestFileType;

import java.io.IOException;
import java.util.List;

public class SoftWrapTest extends AbstractEditorProcessingOnDocumentModificationTest {

  public void testCollapsedRegionWithLongPlaceholderAtLineStart1() throws IOException {
    init("regionToCollapse", TestFileType.TEXT);
    addCollapsedFoldRegion(0, 16, "veryVeryVeryLongPlaceholder");
    EditorTestUtil.configureSoftWraps(myEditor, 10);
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(0, myEditor.getDocument().getTextLength());
    assertEquals(0, softWraps.size());
  }

  public void testCollapsedRegionWithLongPlaceholderAtLineStart2() throws IOException {
    init("regionToCollapse-foo", TestFileType.TEXT);
    addCollapsedFoldRegion(0, 16, "veryVeryVeryLongPlaceholder");
    EditorTestUtil.configureSoftWraps(myEditor, 10);
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(0, myEditor.getDocument().getTextLength());
    assertEquals(1, softWraps.size());
    assertEquals(16, softWraps.get(0).getStart());
  }

  public void testCollapsedRegionWithLongPlaceholderAtLineStart3() throws IOException {
    init("regionToCollapse\nvery long text", TestFileType.TEXT);
    addCollapsedFoldRegion(0, 16, "veryVeryVeryLongPlaceholder");
    EditorTestUtil.configureSoftWraps(myEditor, 10);
    List<? extends SoftWrap> softWraps = myEditor.getSoftWrapModel().getSoftWrapsForRange(0, myEditor.getDocument().getTextLength());
    assertEquals(1, softWraps.size());
    assertEquals(16, softWraps.get(0).getStart());
  }
}
