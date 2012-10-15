/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.execution;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Nadya Zabrodina
 */
public class HgDeleteModifyPromptHandler implements HgPromptHandler {

  private static final Logger LOG = Logger.getInstance("#org.zmlx.hg4idea.execution.HgDeleteModifyPromptHandler");

  private static final Pattern LOCAL_DELETE_REMOTE_MODIFIED_CONFLICT_MESSAGE_PATTERN = Pattern.compile(
    "remote\\schanged(.+)which\\slocal\\sdeleted\\s.+");
  private static final Pattern REMOTE_DELETE_LOCAL_MODIFIED_CONFLICT_MESSAGE_PATTERN = Pattern.compile(
    "\\slocal\\schanged(.+)which\\sremote\\sdeleted\\s.+");

  private String myMessage = "";

  public HgPromptChoice promptUser(final String message,
                                   @NotNull final HgPromptChoice[] choices,
                                   @NotNull final HgPromptChoice defaultChoice) {

    final int[] chosen = new int[]{-1};
    try {
      EventQueue.invokeAndWait
        (new Runnable() {
          public void run() {
            String[] choicePresentationArray = new String[choices.length];
            for (int i = 0; i < choices.length; ++i) {
              choicePresentationArray[i] = choices[i].toString();
            }
            chosen[0] = Messages
              .showChooseDialog(myMessage, "Delete-Modify Conflict",
                                choicePresentationArray,
                                defaultChoice.toString(), Messages.getQuestionIcon());
          }
        });
    }
    catch (InterruptedException e) {
      LOG.error(e);
      return defaultChoice;
    }
    catch (InvocationTargetException e) {
      LOG.error(e);
      return defaultChoice;
    }
    return chosen[0] >= 0 ? choices[chosen[0]] : HgPromptChoice.ABORT;
  }

  public boolean shouldHandle(String message) {
    Matcher localDelMatcher = LOCAL_DELETE_REMOTE_MODIFIED_CONFLICT_MESSAGE_PATTERN.matcher(message);
    Matcher locaModifMatcher = REMOTE_DELETE_LOCAL_MODIFIED_CONFLICT_MESSAGE_PATTERN.matcher(message);
    String filename;
    if (localDelMatcher.matches()) {
      filename = localDelMatcher.group(1);
      myMessage =
        "File " + filename + " is deleted locally, but modified remotely. Do you want to keep the modified version or remove the file?";
      return true;
    }
    else if (locaModifMatcher.matches()) {
      filename = locaModifMatcher.group(1);
      myMessage =
        "File " + filename + " is deleted remotely, but modified locally. Do you want to keep the modified version or remove the file?";
      return true;
    }
    return false;
  }
}
