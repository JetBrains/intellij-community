// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConfirmationDialog extends OptionsMessageDialog {
  private final VcsShowConfirmationOption myOption;
  private String myDoNotShowAgainMessage;
  private final String myOkActionName;
  private final String myCancelActionName;

  public static boolean requestForConfirmation(@NotNull VcsShowConfirmationOption option,
                                               @NotNull Project project,
                                               @NotNull String message,
                                               @NotNull String title,
                                               @Nullable Icon icon) {
    return requestForConfirmation(option, project, message, title, icon, null, null);
  }

  public static boolean requestForConfirmation(@NotNull VcsShowConfirmationOption option,
                                               @NotNull Project project,
                                               @NotNull String message,
                                               @NotNull String title,
                                               @Nullable Icon icon,
                                               @Nullable String okActionName,
                                               @Nullable String cancelActionName) {
    if (option.getValue() == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) return false;
    final ConfirmationDialog dialog = new ConfirmationDialog(project, message, title, icon, option, okActionName, cancelActionName);
    if (!option.isPersistent()) {
      dialog.setDoNotAskOption(null);
    }
    else {
      dialog.setDoNotShowAgainMessage(UIBundle.message("dialog.options.do.not.ask"));
    }
    return dialog.showAndGet();
  }

  public ConfirmationDialog(Project project, final String message, String title, final Icon icon, final VcsShowConfirmationOption option) {
    this(project, message, title, icon, option, null, null);
  }

  public ConfirmationDialog(Project project, final String message, String title, final Icon icon, final VcsShowConfirmationOption option,
                            @Nullable String okActionName, @Nullable String cancelActionName) {
    super(project, message, title, icon);
    myOption = option;
    myOkActionName = okActionName != null ? okActionName : CommonBundle.getYesButtonText();
    myCancelActionName = cancelActionName != null ? cancelActionName : CommonBundle.getNoButtonText();
    init();
  }
  
  public void setDoNotShowAgainMessage(final String doNotShowAgainMessage) {
    myDoNotShowAgainMessage = doNotShowAgainMessage;
    myCheckBoxDoNotShowDialog.setText(doNotShowAgainMessage);
  }

  @NotNull
  @Override
  protected String getDoNotShowMessage() {
    return myDoNotShowAgainMessage == null ? super.getDoNotShowMessage() : myDoNotShowAgainMessage;
  }

  @Override
  protected String getOkActionName() {
    return myOkActionName;
  }

  @Override
  protected String getCancelActionName() {
    return myCancelActionName;
  }

  @Override
  protected boolean isToBeShown() {
    return myOption.getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
  }

  @Override
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

  @Override
  protected boolean shouldSaveOptionsOnCancel() {
    return true;
  }
}
