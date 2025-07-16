// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.openapi.util.NlsContexts.*;

/**
 * Use {@link DialogManager#show(DialogWrapper) DialogManager.show(DialogWrapper)} instead of {@link DialogWrapper#show()}
 * to make the code testable:
 * in the test environment such calls will be transferred to the TestDialogManager and can be handled by tests;
 * in the production environment they will be simply delegated to DialogWrapper#show().
 *
 * @author Kirill Likhodedov
 */
public class DialogManager {

  public static void show(@NotNull DialogWrapper dialog) {
    dialogManager().showDialog(dialog);
  }

  public static int showMessage(final @NotNull @DialogMessage String message,
                                final @NotNull @DialogTitle String title,
                                final String @NotNull @Button [] options,
                                final int defaultButtonIndex,
                                final int focusedButtonIndex,
                                final @Nullable Icon icon,
                                final @Nullable DoNotAskOption dontAskOption) {
    return dialogManager().showMessageDialog(message, title, options, defaultButtonIndex, focusedButtonIndex, icon, dontAskOption);
  }

  public static int showOkCancelDialog(@NotNull Project project,
                                       @NotNull @DialogMessage String message,
                                       @NotNull @DialogTitle String title,
                                       @NotNull @Button String okButtonText,
                                       @NotNull @Button String cancelButtonText,
                                       @Nullable Icon icon) {
    return dialogManager().showMessageDialog(project, message, title, new String[]{okButtonText, cancelButtonText}, 0, icon);
  }

  public static int showYesNoCancelDialog(@NotNull Project project,
                                          @NotNull @DialogMessage String message,
                                          @NotNull @DialogTitle String title,
                                          @NotNull @Button String yesButtonText,
                                          @NotNull @Button String noButtonText,
                                          @NotNull @Button String cancelButtonText,
                                          @Nullable Icon icon) {
    return dialogManager()
      .showMessageDialog(project, message, title, new String[]{yesButtonText, noButtonText, cancelButtonText}, 0, 1, icon);
  }

  protected void showDialog(@NotNull DialogWrapper dialog) {
    dialog.show();
  }

  protected int showMessageDialog(@NotNull Project project,
                                  @NotNull @DialogMessage String message,
                                  @NotNull @DialogTitle String title,
                                  String @NotNull @Button [] options,
                                  int defaultButtonIndex,
                                  @Nullable Icon icon) {
    return Messages.showDialog(project, message, title, options, defaultButtonIndex, icon);
  }

  protected int showMessageDialog(@NotNull Project project,
                                  @NotNull @DialogMessage String message,
                                  @NotNull @DialogTitle String title,
                                  String @NotNull @Button [] options,
                                  int defaultButtonIndex,
                                  int focusedButtonIndex,
                                  @Nullable Icon icon) {
    return Messages.showDialog(project, message, title, null, options, defaultButtonIndex, focusedButtonIndex, icon);
  }

  protected int showMessageDialog(@NotNull @DialogMessage String message,
                                  @NotNull @DialogTitle String title,
                                  String @NotNull @Button [] options,
                                  int defaultButtonIndex,
                                  int focusedButtonIndex,
                                  @Nullable Icon icon,
                                  @Nullable DoNotAskOption dontAskOption) {
    return Messages.showDialog(message, title, options, defaultButtonIndex, focusedButtonIndex, icon, dontAskOption);
  }

  private static @NotNull DialogManager dialogManager() {
    return ApplicationManager.getApplication().getService(DialogManager.class);
  }
}
