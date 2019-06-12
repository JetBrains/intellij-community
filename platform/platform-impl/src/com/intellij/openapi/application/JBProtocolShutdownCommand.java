// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
final class JBProtocolShutdownCommand extends JBProtocolCommand {
  JBProtocolShutdownCommand() {
    super("shutdown");
  }

  @Override
  public void perform(String target, @NotNull Map<String, String> parameters) {
    if (StringUtil.isEmpty(target)) {
      ApplicationManager.getApplication().exit();
    }
    else {
      MessageDialogBuilder.YesNo confirmExitDialog = MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"), target)
        .yesText(ApplicationBundle.message("command.exit")).noText(
          CommonBundle.message("button.cancel"));
      if (confirmExitDialog.show() == Messages.YES) {
        ApplicationManagerEx.getApplicationEx().exit(true, true);
      }
    }
  }
}
