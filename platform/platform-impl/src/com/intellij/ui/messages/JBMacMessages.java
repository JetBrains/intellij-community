// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.messages;

import com.intellij.BundleBase;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.ModalityHelper;
import com.intellij.ui.mac.MacMessages;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@Service
@ApiStatus.Internal
public final class JBMacMessages extends MacMessages {
  @Override
  public int showYesNoCancelDialog(@NotNull String title,
                                   @NotNull String message,
                                   @NotNull String yesText,
                                   @NotNull String noText,
                                   @NotNull String cancelText,
                                   @Nullable Window window,
                                   @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (window == null) {
      window = getForemostWindow();
    }

    String defaultButtonCleaned = yesText.replace(BundleBase.MNEMONIC_STRING, "");
    String otherButtonCleaned = cancelText.replace(BundleBase.MNEMONIC_STRING, "");
    String alternateButtonCleaned = noText.replace(BundleBase.MNEMONIC_STRING, "");
    SheetMessage sheetMessage = new SheetMessage(window, title, message, UIUtil.getQuestionIcon(),
                                                 new String [] {
                                                   defaultButtonCleaned,
                                                   otherButtonCleaned,
                                                   alternateButtonCleaned
                                                 },
                                                 doNotAskOption, yesText, noText);

    String resultString = sheetMessage.getResult();
    int result = resultString.equals(defaultButtonCleaned) ? Messages.YES : resultString.equals(alternateButtonCleaned) ? Messages.NO : Messages.CANCEL;
    if (doNotAskOption != null) {
        doNotAskOption.setToBeShown(sheetMessage.toBeShown(), result);
    }
    return result;
  }

  @Override
  public int showMessageDialog(@NotNull String title,
                               String message,
                               String @NotNull [] buttons,
                               boolean errorStyle,
                               @Nullable Window window,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption) {
    if (window == null) {
      window = getForemostWindow();
    }

    Icon icon = errorStyle ? UIUtil.getErrorIcon() : UIUtil.getInformationIcon();

    final String defaultOptionTitle = defaultOptionIndex != -1 ? buttons[defaultOptionIndex] : null;
    final String focusedButtonTitle = focusedOptionIndex != -1 ? buttons[focusedOptionIndex] : null;

    final SheetMessage sheetMessage = new SheetMessage(window, title, message, icon, buttons, doNotAskDialogOption, defaultOptionTitle, focusedButtonTitle);
    String result = sheetMessage.getResult();
    for (int i = 0; i < buttons.length; i++) {
      if (result.equals(buttons[i])) {
        if (doNotAskDialogOption != null) {
          doNotAskDialogOption.setToBeShown(sheetMessage.toBeShown(), i);
        }
        return i;
      }
    }
    return -1;
  }

  @Override
  public void showOkMessageDialog(@NotNull String title, String message, @NotNull String okText, @Nullable Window window) {
    new SheetMessage(window == null ? getForemostWindow() : window, title, message,
                     UIUtil.getInformationIcon(), new String[]{okText}, null, okText, okText);
  }

  static @NotNull Window getForemostWindow() {
    Window window = null;
    IdeFocusManager ideFocusManager = IdeFocusManager.getGlobalInstance();

    Component focusOwner = IdeFocusManager.findInstance().getFocusOwner();
    // Let's ask for a focused component first
    if (focusOwner != null) {
      window = SwingUtilities.getWindowAncestor(focusOwner);
    }

    if (window == null) {
      // Looks like ide lost focus, let's ask about the last focused component
      focusOwner = ideFocusManager.getLastFocusedFor(ideFocusManager.getLastFocusedIdeWindow());
      if (focusOwner != null) {
        window = SwingUtilities.getWindowAncestor(focusOwner);
      }
    }

    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    }

    if (window == null) {
      window = WindowManager.getInstance().findVisibleFrame();
    }

    while (window != null) {
      // We have successfully found the window
      // Let's check that we have not missed a blocker
      if (ModalityHelper.isModalBlocked(window)) {
        Window result = ModalityHelper.getModalBlockerFor(window);
        if (result == null) {
          break;
        }
        window = result;
      }
      else {
        break;
      }
    }

    while (window != null && MacUtil.getWindowTitle(window) == null) {
      window = window.getOwner();
      //At least our frame should have a title
    }

    while (window instanceof JDialog && Registry.is("skip.untitled.windows.for.mac.messages", false) && !((JDialog)window).isModal()) {
      window = window.getOwner();
    }

    while (window != null && window.getParent() != null && WindowManager.getInstance().isNotSuggestAsParent(window)) {
      window = window.getOwner();
    }

    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window == null) {
        throw new IllegalStateException("Cannot find any window");
      }
    }
    return window;
  }

  @Override
  public boolean showYesNoDialog(@NotNull String title,
                                 @NotNull String message,
                                 @NotNull String yesText,
                                 @NotNull String noText,
                                 @Nullable Window window,
                                 @Nullable DialogWrapper.DoNotAskOption doNotAskDialogOption) {
    SheetMessage sheetMessage = new SheetMessage(window == null ? getForemostWindow() : window, title, message, UIUtil.getQuestionIcon(),
                                                 new String[]{yesText, noText}, doNotAskDialogOption, yesText, noText);
    boolean result = sheetMessage.getResult().equals(yesText);
    if (doNotAskDialogOption != null && (result || doNotAskDialogOption.shouldSaveOptionsOnCancel())) {
      doNotAskDialogOption.setToBeShown(sheetMessage.toBeShown(), result ? Messages.YES : Messages.NO);
    }
    return result;
  }

  @Override
  public void showErrorDialog(@NotNull String title, String message, @NotNull String okButton, @Nullable Window window) {
    new SheetMessage(window == null ? getForemostWindow() : window, title, message, UIUtil.getErrorIcon(), new String [] {okButton}, null, null, okButton);
  }
}
