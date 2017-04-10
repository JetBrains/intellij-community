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
package com.intellij.openapi.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.processing.HighlightMode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class BaseDiffTestCase extends PlatformTestCase {
  private File myFile1;
  private File myFile2;
  public static final DiffContent.Listener SHOULD_NOT_INVALIDATE = new DiffContent.Listener() {
    @Override
    public void contentInvalid() {
      fail();
    }
  };

  public static String readFile(File file) throws IOException {
    FileInputStream stream = new FileInputStream(file);
    byte[] bytes = new byte[(int) file.length()];
    stream.read(bytes);
    return LineTokenizer.correctLineSeparators(new String(bytes));
  }

  protected DiffPanelImpl createDiffPanel(Window ownerWindow, Project project, boolean enableToolbar) {
    DiffPanelImpl diffPanel = new DiffPanelImpl(ownerWindow, project, enableToolbar, true,
                                                DiffManagerImpl.FULL_DIFF_DIVIDER_POLYGONS_OFFSET, null);
    Disposer.register(project, diffPanel);
    return diffPanel;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DiffManagerImpl.getInstanceEx().setComparisonPolicy(ComparisonPolicy.DEFAULT);
    DiffManagerImpl.getInstanceEx().setHighlightMode(HighlightMode.BY_WORD);
  }

  @Override
  protected void tearDown() throws Exception {
    myFile1 = null;
    myFile2 = null;
    super.tearDown();
  }

  public static File getFile(String name) {
    return new File(getDirectory(), name);
  }

  public static File getDirectory() {
    return new File(PlatformTestUtil.getPlatformTestDataPath(), "diff");
  }

  protected void setFile1(String file1) { myFile1 = getFile(file1); }

  protected void setFile2(String file2) { myFile2 = getFile(file2); }

  protected DiffPanelImpl loadFiles() throws IOException {
    DiffPanelImpl diffPanel = createDiffPanel(null, myProject, false);
    String content1 = content1();
    String content2 = content2();
    setContents(diffPanel, content1, content2);
    return diffPanel;
  }

  protected String content2() throws IOException {
    return readFile(myFile2);
  }

  protected String content1() throws IOException {
    return readFile(myFile1);
  }

  protected void checkTextEqual(String content, Editor editor) {
    assertEquals(content.replaceAll("\r\n", "\n"), editor.getDocument().getText());
  }

  protected void setContents(final DiffPanelImpl diffPanel, final String content1, final String content2) {
    diffPanel.setContents(new SimpleContent(content1),  new SimpleContent(content2));
  }

  protected static Editor getEditor2(DiffPanelImpl diffPanel) {
    return diffPanel.getEditor(FragmentSide.SIDE2);
  }

  protected static Editor getEditor1(DiffPanelImpl diffPanel) {
    return diffPanel.getEditor(FragmentSide.SIDE1);
  }

  protected void replaceString(final Document document, final int startOffset, final int endOffset, final String string) {
    replaceString2(myProject, document, startOffset, endOffset, string);
  }

  public static void replaceString2(final Project project, final Document document,
                                     final int startOffset,
                                     final int endOffset,
                                     final String string) {
    ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, () -> document.replaceString(startOffset, endOffset, string), null, null));
  }
}
