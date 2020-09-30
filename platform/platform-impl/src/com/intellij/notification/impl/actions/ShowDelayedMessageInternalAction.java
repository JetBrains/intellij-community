// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Denis Fokin
 */
@SuppressWarnings("HardCodedStringLiteral")
public final class ShowDelayedMessageInternalAction extends AnAction implements DumbAware{
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new Thread("show delayed msg") {
      @Override
      public void run() {
        super.run();

        TimeoutUtil.sleep(3000);

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> MessageDialogBuilder.yesNo("Nothing happens after that", "Some message goes here").yesText(
          ApplicationBundle.message("command.exit")).noText(
          CommonBundle.getCancelButtonText()).guessWindowAndAsk());
      }
    }.start();

  }
}
