/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.ide.impl.ProjectNewWindowDoNotAskOption;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;

/**
 * @author Denis Fokin
 */
public class CheckMessagesButtonsOrderAction  extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {

    new Thread("check messages") {
      @Override
      public void run() {
        super.run();

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {

            // Case  0

            String message = 2 + " usage" + (2 > 1 ? "s were" : " was") +
                             " found in comments and non-code files.\nWould you like to rename " + (2 > 1 ? "them" : "it") + "?";
            String[] options = {"Show Usages", "Cancel", "Rename Only Code Usages", "Rename All Usages"};
            int focusedOptionIndex = 2;
            Messages.showDialog(message, "Rename", options, 0, focusedOptionIndex, Messages.getQuestionIcon(), null);


            // Case 1

            Messages.showDialog("unchecked unfocused checkbox, [Proceed], ____,Cancel, *Show ussages*",
                                "Message 1", new String [] {"Show usages", "Cancel", "Proceed"}, 0, 0,
                                Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 2

            Messages.showDialog("unchecked unfocused checkbox, [Proceed], ____,Cancel, *Show ussages*",
                                "Message 2", new String [] {"Show usages", "Cancel", "Proceed"}, 0, 1,
                                Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 3

            Messages.showDialog("unchecked unfocused checkbox, [Proceed], ____, *Cancel*, Show ussages",
                                "Message 3", new String [] {"Show usages", "Cancel", "Proceed"}, 1, 0,
                                Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 4

            Messages.showDialog("unchecked unfocused checkbox, [Proceed], ____, *Cancel*, Show ussages",
                                "Message 4", new String [] {"Show usages", "Cancel", "Proceed"}, 1, 1,
                                Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 5

            Messages.showDialog("[Proceed], ____, Cancel, *Show ussages*",
                                "Message 5", new String [] {"Show usages", "Cancel", "Proceed"}, 0, 1,
                                Messages.getQuestionIcon(), null);

            // Case 6

            Messages.showYesNoCancelDialog("unchecked unfocused checkbox, [New Window], ____, Cancel, *This Window*",
                                           "Message 6",
                                           "This window",
                                           "New Window",
                                           CommonBundle.getCancelButtonText(),
                                           Messages.getQuestionIcon(),
                                           new ProjectNewWindowDoNotAskOption());

            // Case 7

            Messages.showDialog("[Variant 1], Variant 2, Variant 3, Cancel, *Ok*",
                                "Message 7",
                                new String[]{"Ok", "Cancel", "Variant 3", "Variant 2", "Variant 1"},
                                0, 1, Messages.getQuestionIcon(), null);

            // Case 8

            Messages.showDialog( "unchecked unfocused checkbox, [Variant 1], Variant 2, Variant 3, Cancel, *Ok*",
                                 "Message 8",
                                 new String [] {"Ok", "Cancel", "Variant 3", "Variant 2", "Variant 1"},
                                 0, 0, Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 9

            Messages.showDialog( "unchecked unfocused checkbox, [Variant 1], Variant 2, Variant 3, *Cancel*, Ok",
                                 "Message 9",
                                 new String [] {"Ok", "Cancel", "Variant 3", "Variant 2", "Variant 1"},
                                 1, 1, Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 10

            Messages.showDialog( "unchecked unfocused checkbox, [Variant 1], Variant 2, Variant 3, Cancel, *Ok*",
                                 "Message 10",
                                 new String [] {"Ok", "Cancel", "Variant 3", "Variant 2", "Variant 1"},
                                 0, 1, Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 11

            Messages.showDialog( "unchecked unfocused checkbox, [Variant 1], Variant 2, Variant 3, Cancel, *Ok*",
                                 "Message 11",
                                 new String [] {"Ok", "Cancel", "Variant 3", "Variant 2", "Variant 1"},
                                 0, 1, Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 12

            Messages.showDialog( "unchecked unfocused checkbox, [Cancel], Variant 1, Variant 2, Variant 3, *Ok*",
                                 "Message 12",
                                 new String [] {"Ok", "Variant 3", "Variant 2", "Variant 1", "Cancel"},
                                 0, 1, Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

            // Case 13

            Messages.showDialog( "unchecked unfocused checkbox, [Cancel], Variant 1, Ok, Variant 2, Variant 3, *Ok*",
                                 "Message 13",
                                 new String [] {"Variant 3", "Variant 2", "Ok", "Variant 1", "Cancel"},
                                 0, 1, Messages.getQuestionIcon(), new ProjectNewWindowDoNotAskOption());

          }
        });

      }
    }.start();

  }
}
