/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;

import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class JBProtocolShutdownCommand extends JBProtocolCommand {

  public JBProtocolShutdownCommand() {
    super("shutdown");
  }

  @Override
  public void perform(String target, Map<String, String> parameters) {
    if (StringUtil.isEmpty(target)) {
      ApplicationManager.getApplication().exit();
    } else {
      MessageDialogBuilder.YesNo confirmExitDialog = MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"), target)
        .yesText(ApplicationBundle.message("command.exit")).noText(
          CommonBundle.message("button.cancel"));
      if (confirmExitDialog.show() == Messages.YES) {
        ApplicationManagerEx.getApplicationEx().exit(true, true);
      }
    }
  }
}
