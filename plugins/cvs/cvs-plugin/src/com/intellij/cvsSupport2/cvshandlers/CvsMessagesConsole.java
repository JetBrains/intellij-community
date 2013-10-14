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
package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.CvsBundle;
import com.intellij.util.ui.EditorAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.CvsMessagesAdapter;
import com.intellij.cvsSupport2.cvsoperations.cvsMessages.MessageEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
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

  public void connectToOutputView(@NotNull Editor editor, Project project) {
    myOutput = new EditorAdapter(editor, project, true);
  }

  @Override
  public void addMessage(final MessageEvent event) {
    if (hasNotEmptyMessage(event)) {
      appendString(event.getMessage(), getAttributesFor(event));
    }
  }

  private void appendString(String message, TextAttributes attributes) {
    if (myOutput == null) return;
    myOutput.appendString(message, attributes);
  }

  private static TextAttributes getAttributesFor(MessageEvent event) {
    return event.isError() || event.isTagged() ? USER_MESSAGES_ATTRIBUTES : PROGRESS_MESSAGES_ATTRIBUTES;

  }

  private static boolean hasNotEmptyMessage(final MessageEvent event) {
    return !event.getMessage().isEmpty();
  }

  @Override
  public void commandStarted(String command) {
    appendString(command, COMMAND);
  }

  @Override
  public void commandFinished(String commandName, long time) {
    appendString(CvsBundle.message("message.command.finished", time / 1000), COMMAND);
  }

}