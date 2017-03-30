/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.content;

import com.intellij.openapi.diff.BaseDiffTestCase;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.FragmentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeFuncTest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;

public class FragmentContentTest extends BaseDiffTestCase {
  private Document myDocument;
  private FragmentContent myContent;
  private DiffPanelImpl myDiffPanel;
  private Document myFragment;
  private DocumentContent myOriginalContent;
  public static final Condition<RangeHighlighter> ACTION_HIGHLIGHTER = rangeHighlighter -> {
    GutterIconRenderer iconRenderer = (GutterIconRenderer)rangeHighlighter.getGutterIconRenderer();
    if (iconRenderer == null) return false;
    return iconRenderer.getClickAction() != null;
  };

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDocument = createDocument("0123456789");
    myOriginalContent = new DocumentContent(myProject, myDocument);
    myContent = new FragmentContent(myOriginalContent, new TextRange(3, 7), myProject, FileTypes.PLAIN_TEXT);
    myDiffPanel = createDiffPanel(null, myProject, false);
    myDiffPanel.setContents(myContent, new SimpleContent("1"));
    myFragment = myContent.getDocument();
    myContent.addListener(SHOULD_NOT_INVALIDATE);
  }

  @Override
  protected void tearDown() throws Exception {
    myDocument = null;
    myContent = null;
    myDiffPanel = null;
    myFragment = null;
    myOriginalContent = null;
    super.tearDown();
  }

  public void testSynchonization() {
    assertEquals("3456", myFragment.getText());
    replaceString(myFragment, 1, 3, "xy");
    assertEquals("0123xy6789", myDocument.getText());
    replaceString(myDocument, 4, 6, "45");
    assertEquals("0123456789", myOriginalContent.getDocument().getText());
    assertEquals("3456", myFragment.getText());
    replaceString(myDocument, 0, 1, "xyz");
    assertEquals("3456", myFragment.getText());
    replaceString(myFragment, 1, 3, "xy");
    assertEquals("xyz123xy6789", myDocument.getText());
  }

  public void testEditReadonlyDocument() {
    SimpleContent content = new SimpleContent("abc");
    FragmentContent fragment = new FragmentContent(content, new TextRange(1, 2), myProject, FileTypes.PLAIN_TEXT);
    fragment.onAssigned(true);
    Document document = fragment.getDocument();
    assertEquals("b", document.getText());
    assertFalse(document.isWritable());
    assertFalse(content.getDocument().isWritable());
    fragment.onAssigned(false);
  }

  public void testOriginalBecomesReadOnly() {
    SimpleContent content = new SimpleContent("abc");
    content.setReadOnly(false);
    FragmentContent fragment = new FragmentContent(content, new TextRange(1, 2), myProject, FileTypes.PLAIN_TEXT);
    DiffPanelImpl diffPanel = createDiffPanel(null, myProject, false);
    diffPanel.setContents(content, fragment);
    JComponent component = diffPanel.getComponent();
    component.addNotify();
    assertNotNull(ContainerUtil.find(diffPanel.getEditor1().getMarkupModel().getAllHighlighters(), ACTION_HIGHLIGHTER));
    //fragment.onAssigned(true);
    Document document = fragment.getDocument();
    content.getDocument().setReadOnly(true);
    assertFalse(document.isWritable());
    component.removeNotify();
  }

  public void testRemoveOriginalFragment() {
    myContent.removeListener(SHOULD_NOT_INVALIDATE);
    Editor editor = myDiffPanel.getEditor(FragmentSide.SIDE2);
    assertNotNull(MergeFuncTest.findAction(editor, 0, ""));
    replaceString(myDocument, 2, 8, "");
    assertEquals("0189", myDocument.getText());
    assertNull(myDiffPanel.getEditor(FragmentSide.SIDE1));
    assertNull(MergeFuncTest.findAction(editor, 0, ""));
  }

  private Document createDocument(String text) {
    return EditorFactory.getInstance().createDocument(text);
  }

  public void testRemoveListeners() {
    replaceString(myFragment, 0, 1, "x");
    assertEquals("012x456789", myDocument.getText());
    myDiffPanel.setContents(new SimpleContent("1"), new SimpleContent("2"));
    replaceString(myFragment, 0, 1, "3");
    assertEquals("012x456789", myDocument.getText());
    replaceString(myDocument, 3, 4, "y");
    assertEquals("012y456789", myDocument.getText());
    assertEquals("3456", myFragment.getText());
  }
}
