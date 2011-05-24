package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jdom.Element;

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
    String text = "xxx<caret>   \nyyy   ";
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
    myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getText().indexOf("Z")+1);
    type('Z');

    stripTrailingSpaces();
    checkResultByText("xxx\nZZ<caret>");
  }

  private void doTest(String before, String after) throws IOException {
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
        ((DocumentImpl)getEditor().getDocument()).stripTrailingSpaces(true);
      }
    });
  }
}
