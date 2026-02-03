// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.common.EditorCaretTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;

public class TrailingSpacesStripperTest extends LightPlatformCodeInsightTestCase {
  private EditorSettingsExternalizable.OptionSet oldSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    oldSettings = settings.getState();
    settings.loadState(new EditorSettingsExternalizable.OptionSet());
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
    settings.setVirtualSpace(false);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EditorSettingsExternalizable.getInstance().loadState(oldSettings);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void doTest(@NonNls String before, @NonNls String after) {
    configureFromFileText("x.txt", before);
    type(' ');
    backspace();
    stripTrailingSpaces();
    checkResultByText(after);
  }

  private void stripTrailingSpaces() {
    WriteCommandAction.runWriteCommandAction(null, () -> {
      TrailingSpacesStripper.strip(getEditor().getDocument(), true, true);
    });
  }

  public void testDoNotStripModifiedOnCurrentLine() {
    doTest("xxx\n   <caret>\nyyy",
           "xxx\n   <caret>\nyyy");
  }
  public void testDoStripModifiedOnCurrentLineIfCaretWouldNotJump() {
    doTest("xxx\n   222<caret>    \nyyy",
           "xxx\n   222<caret>\nyyy");
  }
  public void testDoNotStripModifiedOnCurrentLastLine() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setEnsureNewLineAtEOF(true);

    configureFromFileText("x.txt", "xxx\n        <caret>");
    type(' ');

    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx\n         <caret>\n");
  }

  public void testStrippingWithMultipleCarets() {
    doTest("xxx\n   <caret>\nyyy<caret>  ",
           "xxx\n   <caret>\nyyy<caret>");
  }

  public void testModifyAndAltTabAway() {
    configureFromFileText("x.txt", "xxx<caret>\nyyy");
    type(' ');

    FocusEvent event = new FocusEvent(getEditor().getContentComponent(), 1005);
    FocusListener[] listeners = getEditor().getContentComponent().getListeners(FocusListener.class);
    for (FocusListener listener : listeners) {
      listener.focusLost(event);
    }

    stripTrailingSpaces();
    checkResultByText("xxx <caret>\nyyy");
  }

  public void testOnlyModifiedLinesGetStripped() {
    @NonNls String text = "xxx<caret>   \nyyy   ";
    configureFromFileText("x.txt", text);
    ((DocumentEx)getEditor().getDocument()).clearLineModificationFlags();
    stripTrailingSpaces();
    checkResultByText(text);
    type('z');

    stripTrailingSpaces();
    checkResultByText("xxxz<caret>\nyyy   ");
  }

  public void testOnlyModifiedLinesWhenDoesNotAllowCaretAfterEndOfLine() {
    configureFromFileText("x.txt", "xxx<caret>   \nZ   ");
    type(' ');
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf('Z') + 1);
    type('Z');

    stripTrailingSpaces();
    checkResultByText("xxx\nZZ<caret>");
  }

  public void testModifyLine_Save_MoveCaret_SaveAgain_ShouldStrip() {
    configureFromFileText("x.txt", "xxx <caret>\nyyy\n");
    type(' ');
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx  <caret>\nyyy\n"); // caret in the way
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().indexOf("yyy"));

    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx\n<caret>yyy\n"); // now we can strip
  }

  public void testDisableStrip_Modify_Save_EnableOnModifiedLines_Modify_Save_ShouldStripModifiedOnly() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);

    configureFromFileText("x.txt", "");
    type("xxx   \nyyy   \nzzz   \n");
    getEditor().getCaretModel().moveToOffset(0);
    end();end();
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx   <caret>\nyyy   \nzzz   \n");

    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
    type(' ');
    getEditor().getCaretModel().moveToOffset(getEditor().getDocument().getText().length());
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx\nyyy   \nzzz   \n<caret>");
  }

  public void testDoNotStripModifiedLines_And_EnsureBlankLineAtTheEnd_LeavesWhitespacesAtTheEndOfFileAlone() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    settings.setEnsureNewLineAtEOF(true);

    Document document = configureFromFileText("x.txt", "xxx <caret>\nyyy\n\t\t\t");
    // make any modification, so that Document and file content differ. Otherwise save won't be, and "on-save" actions won't be called.
    WriteCommandAction.runWriteCommandAction(getProject(),
                                             () -> document.insertString(0, " "));


    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText(" xxx <caret>\nyyy\n\t\t\t\n");
  }

  public void testModifySameLineInTwoFilesAndSaveAllShouldStripAtLeastOneFile() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);

    Editor editor1 = createHeavyEditor("x1.txt", "x11 <caret>\nyyy\n");
    Editor editor2 = createHeavyEditor("x2.txt", "x22 <caret>\nyyy\n");

    type(' ', editor1, getProject());
    type(' ', editor2, getProject());
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals("x11\nyyy\n", editor1.getDocument().getText());
    assertEquals("x22  \nyyy\n", editor2.getDocument().getText()); // caret in the way in second but not in the first
  }

  public void testStripTrailingSpacesAtCaretLineOnExplicitSave() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    settings.setKeepTrailingSpacesOnCaretLine(false);
    configureFromFileText(
      "x.txt",
      "xxx   <caret>\nyyy   "
    );
    type(' ');
    backspace();
    EditorTestUtil.executeAction(
      getEditor(),
      "SaveAll"
    );
    checkResultByText(
      "xxx<caret>\nyyy"
    );
  }

  public void testRemoveTrailingBlankLinesNoNewLine() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setRemoveTrailingBlankLines(true);
    settings.setEnsureNewLineAtEOF(false);
    configureFromFileText(
      "x.txt",
      "xxx\nyyy\n\n\n\n\n\n\n<caret>\n"
    );
    type(' ');
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText(
      "xxx\nyyy<caret>"
    );
  }

  public void testRemoveTrailingBlankLinesNewLine() {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setRemoveTrailingBlankLines(true);
    settings.setEnsureNewLineAtEOF(true);
    configureFromFileText(
      "x.txt",
      "xxx\nyyy\n\n\n\n\n\n\n<caret>\n"
    );
    type(' ');
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText(
      "xxx\nyyy<caret>\n"
    );
  }

  @NotNull
  private Editor createHeavyEditor(@NotNull String name, @NotNull String text) {
    VirtualFile myVFile = WriteAction.compute(() -> {
      try {
        VirtualFile file = getSourceRoot().createChildData(null, name);
        VfsUtil.saveText(file, text);
        return file;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    final FileDocumentManager manager = FileDocumentManager.getInstance();
    final Document document = manager.getDocument(myVFile);
    manager.reloadFromDisk(document);
    Editor editor = createEditor(myVFile);
    EditorCaretTestUtil.CaretAndSelectionState caretsState = EditorTestUtil.extractCaretAndSelectionMarkers(document);
    EditorTestUtil.setCaretsAndSelection(editor, caretsState);
    return editor;
  }
}