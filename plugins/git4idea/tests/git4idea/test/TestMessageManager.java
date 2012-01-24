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
package git4idea.test;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.MessageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Kirill Likhodedov
 */
public class TestMessageManager extends MessageManager {
  
  private Message myLastMessage;
  private int myNextAnswer = DEFAULT_ANSWER;
  private static final int DEFAULT_ANSWER = Messages.CANCEL;

  public static class Message {
    @NotNull private final String myTitle;
    @NotNull private final String myDescription;
    @NotNull private final String myYesText;
    @NotNull private final String myNoText;

    public Message(@NotNull String title, @NotNull String description, @NotNull String yesText, @NotNull String noText) {
      myTitle = title;
      myDescription = description;
      myYesText = yesText;
      myNoText = noText;
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }
  }
  
  @Nullable
  public Message getLastMessage() {
    return myLastMessage;
  }
  
  public void nextAnswer(int answer) {
    myNextAnswer = answer;
  }

  @Override
  protected int doShowYesNoDialog(Project project, String description, String title, String yesText, String noText, @Nullable Icon icon) {
    myLastMessage = new Message(title, description, yesText, noText);
    return myNextAnswer;
  }
  
  
}
