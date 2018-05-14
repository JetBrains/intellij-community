// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.MessageEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.EditorAdapter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * author: lesya
 */
public class CvsMessagesConsole extends CvsMessagesAdapter {

  private EditorAdapter myOutput;
  public static final TextAttributes USER_MESSAGES_ATTRIBUTES = new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
  public static final TextAttributes PROGRESS_MESSAGES_ATTRIBUTES = new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.ITALIC);
  public static final TextAttributes COMMAND = new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.BOLD);

  private int lineLimit = 1000;
  private int currentLines = 0;

  public void connectToOutputView(@NotNull Editor editor, Project project) {
    myOutput = new EditorAdapter(editor, project, true);
  }

  @Override
  public void addMessage(final MessageEvent event) {
    final String message = event.getMessage();
    if (message.isEmpty()) return;
    if (!event.isError() && !event.isTagged()) {
      currentLines++;
      if (currentLines > lineLimit) return;
    }
    appendString(message, getAttributesFor(event));
  }

  private void appendString(String message, TextAttributes attributes) {
    if (myOutput == null) return;
    myOutput.appendString(message, attributes);
  }

  private static TextAttributes getAttributesFor(MessageEvent event) {
    return event.isError() || event.isTagged() ? USER_MESSAGES_ATTRIBUTES : PROGRESS_MESSAGES_ATTRIBUTES;
  }

  @Override
  public void commandStarted(String command) {
    lineLimit = Registry.intValue("cvs.server.output.max.lines", 1000);
    currentLines = 0;
    appendString(command, COMMAND);
  }

  @Override
  public void commandFinished(String commandName, long time) {
    if (currentLines > lineLimit) appendString(CvsBundle.message("message.log.truncated", lineLimit, currentLines - lineLimit),
                                               PROGRESS_MESSAGES_ATTRIBUTES);
    appendString(CvsBundle.message("message.command.finished", time / 1000), COMMAND);
  }
}