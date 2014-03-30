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
package com.intellij.internal.validation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

/**
 * @author Denis Fokin
 */
public class TestMacMessagesSequencesAction extends AnAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    for (int i=0;i<10;i++){
      final int k = i;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showDialog("Message # " + k, "Title of " + k, new String[] { "Option one", "Option two" }, 0, Messages.getQuestionIcon());
        }
      });
    }
  }

}
