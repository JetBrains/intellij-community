package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.consoleView.EditorAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.MessageEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;

import java.awt.*;

/**
 * author: lesya
 */
public class CvsMessagesConsole extends CvsMessagesAdapter {

  private EditorAdapter myOutput;
  private static final TextAttributes USER_MESSAGES_ATTRIBUTES = new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
  private static final TextAttributes PROGRESS_MESSAGES_ATTRIBUTES = new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.ITALIC);
  private static final TextAttributes COMMAND = new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.BOLD);

  public void connectToOutputView(Editor editor, Project project) {
    myOutput = new EditorAdapter(editor, project);
  }

  public void addMessage(final MessageEvent event) {
    if (hasNotEmptyMessage(event)) {
      appendString(event.getMessage(), getAttributesFor(event));
    }
  }

  private void appendString(String message, TextAttributes attributes) {
    if (myOutput == null) return;
    myOutput.appendString(message, attributes);
  }

  private TextAttributes getAttributesFor(MessageEvent event) {
    if (event.isError() || event.isTagged())
      return USER_MESSAGES_ATTRIBUTES;
    else
      return PROGRESS_MESSAGES_ATTRIBUTES;

  }

  private boolean hasNotEmptyMessage(final MessageEvent event) {
    return event.getMessage().length() > 0;
  }

  public void commandStarted(String command) {
    appendString(command, COMMAND);
  }

  public void commandFinished(String commandName, long time) {
    appendString("Command finished ( " + time/1000 + " )", COMMAND);
  }

}