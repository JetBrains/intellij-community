/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConfirmationDialog extends OptionsMessageDialog{

  private final VcsShowConfirmationOption myOption;

  public static boolean requestForConfirmation(@NotNull VcsShowConfirmationOption option,
                                               @NotNull Project project,
                                               @NotNull String message,
                                               @NotNull String title,
                                               @Nullable Icon icon) {
    if (option.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return false;
    final ConfirmationDialog dialog = new ConfirmationDialog(project, message, title, icon, option);
    if (! option.isPersistent()) {
      dialog.setDoNotAskOption(null);
    }
    dialog.show();
    return dialog.isOK();
  }

  public ConfirmationDialog(Project project, final String message, String title, final Icon icon, final VcsShowConfirmationOption option) {
    super(project, message, title, icon);
    myOption = option;
    init();    
  }

  protected String getOkActionName() {
    return CommonBundle.message("button.yes");
  }

  protected String getCancelActionName() {
    return CommonBundle.message("button.no");
  }

  protected boolean isToBeShown() {
    return myOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    final VcsShowConfirmationOption.Value optionValue;

    if (value) {
      optionValue = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    } else {
      if (onOk) {
        optionValue = VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
      } else {
        optionValue = VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY;
      }
    }

    myOption.setValue(optionValue);

  }

  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }
}
