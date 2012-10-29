package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.TrailingSpacesStripper;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.IOException;

/**
 * User: cdr
 */
public class StripTrailingSpacesTest extends LightPlatformCodeInsightTestCase {
  private final Element oldSettings = new Element("temp");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.writeExternal(oldSettings);
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
    settings.setVirtualSpace(false);
  }

  @Override
  protected void tearDown() throws Exception {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.readExternal(oldSettings);
    super.tearDown();
  }

  private void doTest(@NonNls String before, @NonNls String after) throws IOException {
    configureFromFileText("x.txt", before);
    type(' ');
    backspace();
    stripTrailingSpaces();
    checkResultByText(after);
  }

  private static void stripTrailingSpaces() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        TrailingSpacesStripper.stripIfNotCurrentLine(getEditor().getDocument(), true);
      }
    });
  }

  public void testDoNotStripModifiedOnCurrentLine() throws IOException {
    doTest("xxx\n   <caret>\nyyy",
           "xxx\n   <caret>\nyyy");
  }
  public void testDoStripModifiedOnCurrentLineIfCaretWouldNotJump() throws IOException {
    doTest("xxx\n   222<caret>    \nyyy",
           "xxx\n   222<caret>\nyyy");
  }

  public void testModifyAndAltTabAway() throws IOException {
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

  public void testOnlyModifiedLinesGetStripped() throws IOException {
    @NonNls String text = "xxx<caret>   \nyyy   ";
    configureFromFileText("x.txt", text);
    ((DocumentEx)myEditor.getDocument()).clearLineModificationFlags();
    stripTrailingSpaces();
    checkResultByText(text);
    type('z');

    stripTrailingSpaces();
    checkResultByText("xxxz<caret>\nyyy   ");
  }

  public void testOnlyModifiedLinesWhenDoesNotAllowCaretAfterEndOfLine() throws IOException {
    configureFromFileText("x.txt", "xxx<caret>   \nZ   ");
    type(' ');
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getText().indexOf("Z") + 1);
    type('Z');

    stripTrailingSpaces();
    checkResultByText("xxx\nZZ<caret>");
  }

  public void testModifyLineAndExitApplication_ShouldStripEvenWhenCaretIsAtTheChangedLine() throws IOException {
    configureFromFileText("x.txt", "xxx        <caret>\n");
    type(' ');

    ApplicationImpl application = (ApplicationImpl)ApplicationManager.getApplication();
    application.setDisposeInProgress(true);

    try {
      FileDocumentManager.getInstance().saveAllDocuments();
      checkResultByText("xxx<caret>\n");
    }
    finally {
      application.setDisposeInProgress(false);
    }
  }

  public void testModifyLine_Save_MoveCaret_SaveAgain_ShouldStrip() throws IOException {
    configureFromFileText("x.txt", "xxx <caret>\nyyy\n");
    type(' ');
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx  <caret>\nyyy\n"); // caret in the way
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getText().indexOf("yyy"));

    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx\n<caret>yyy\n"); // now we can strip
  }

  public void testDisableStrip_Modify_Save_EnableOnModifiedLines_Modify_Save_ShouldStripModifiedOnly() throws IOException {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);

    configureFromFileText("x.txt", "");
    type("xxx   \nyyy   \nzzz   \n");
    myEditor.getCaretModel().moveToOffset(0);
    end();end();
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx   <caret>\nyyy   \nzzz   \n");

    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
    type(' ');
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getText().length());
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText("xxx\nyyy   \nzzz   \n<caret>");
  }

  public void testDoNotStripModifiedLines_And_EnsureBlankLineAtTheEnd_LeavesWhitespacesAtTheEndOfFileAlone() throws IOException {
    EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
    settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    settings.setEnsureNewLineAtEOF(true);

    Document document = configureFromFileText("x.txt", "xxx <caret>\nyyy\n\t\t\t");
    // make any modification, so that Document and file content differ. Otherwise save won't be, and "on-save" actions won't be called.
    document.insertString(0, " ");

    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByText(" xxx <caret>\nyyy\n\t\t\t\n");
  }
}
