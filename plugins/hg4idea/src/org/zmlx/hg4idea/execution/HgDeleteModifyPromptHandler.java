// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgDeleteModifyPromptHandler implements HgPromptHandler {

  private static final Logger LOG = Logger.getInstance(HgDeleteModifyPromptHandler.class);

  private static final Pattern LOCAL_DELETE_REMOTE_MODIFIED_CONFLICT_MESSAGE_PATTERN = Pattern.compile(
    "remote\\schanged(.+)which\\slocal\\sdeleted\\s.+");
  private static final Pattern REMOTE_DELETE_LOCAL_MODIFIED_CONFLICT_MESSAGE_PATTERN = Pattern.compile(
    "\\slocal\\schanged(.+)which\\sremote\\sdeleted\\s.+");


  @Override
  public HgPromptChoice promptUser(final @NotNull String message,
                                   final HgPromptChoice @NotNull [] choices,
                                   final @NotNull HgPromptChoice defaultChoice) {

    Matcher localDelMatcher = LOCAL_DELETE_REMOTE_MODIFIED_CONFLICT_MESSAGE_PATTERN.matcher(message);
    Matcher localModifyMatcher = REMOTE_DELETE_LOCAL_MODIFIED_CONFLICT_MESSAGE_PATTERN.matcher(message);
    String filename;
    final String modifiedMessage;
    if (localDelMatcher.matches()) {
      filename = localDelMatcher.group(1);
      modifiedMessage = HgBundle.message("hg4idea.delete.modify.file.deleted.locally", filename);
    }
    else if (localModifyMatcher.matches()) {
      filename = localModifyMatcher.group(1);
      modifiedMessage = HgBundle.message("hg4idea.delete.modify.file.deleted.remotely", filename);
    }
    else {
      modifiedMessage = message;
    }
    final int[] chosen = new int[]{-1};
    try {
      EventQueue.invokeAndWait
        (() -> {
          String[] choicePresentationArray = new String[choices.length];
          for (int i = 0; i < choices.length; ++i) {
            choicePresentationArray[i] = choices[i].toString();
          }
          chosen[0] = Messages
            .showDialog(modifiedMessage, HgBundle.message("hg4idea.delete.modify.conflict.title"),
                        choicePresentationArray, defaultChoice.getChosenIndex(),
                        Messages.getQuestionIcon());
        });
    }
    catch (InterruptedException | InvocationTargetException e) {
      LOG.error(e);
      return defaultChoice;
    }
    return chosen[0] >= 0 ? choices[chosen[0]] : HgPromptChoice.ABORT;
  }

  @Override
  public boolean shouldHandle(@Nullable String message) {
    if (message == null) {
      return false;
    }
    Matcher localDelMatcher = LOCAL_DELETE_REMOTE_MODIFIED_CONFLICT_MESSAGE_PATTERN.matcher(message);
    Matcher localModifyMatcher = REMOTE_DELETE_LOCAL_MODIFIED_CONFLICT_MESSAGE_PATTERN.matcher(message);
    if (localDelMatcher.matches() || localModifyMatcher.matches()) {
      return true;
    }
    return false;
  }
}
