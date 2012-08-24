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
package com.intellij.internal.validation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Konstantin Bulenkov
 */
public class TestMacMessagesAction extends AnAction {
  static int num = 1;
  @Override
  public void actionPerformed(final AnActionEvent e) {
    new DialogWrapper(e.getProject()) {
      {
        setSize(500, 500);
        setTitle("Dialog 1");
        init();
      }

      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        final JButton button = new JButton("Click me");
        button.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent event) {
            new DialogWrapper(e.getProject()) {
              {
                setSize(400, 400);
                setTitle("Dialog 2");
                init();
              }
              @Nullable
              @Override
              protected JComponent createCenterPanel() {
                final JButton b = new JButton("Click me again " + num);
                num++;
                b.addActionListener(new ActionListener() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                    Messages.showYesNoDialog(b, "Blah-blah", "Error", Messages.getQuestionIcon());
                  }
                });
                return b;
              }
            }.show();
          }
        });
        return button;
      }
    }.show();
  }
}
