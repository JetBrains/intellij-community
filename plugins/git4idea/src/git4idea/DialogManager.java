package git4idea;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  public static int showMessage(@NotNull final String description,
                                @NotNull final String title,
                                @NotNull final String[] options,
                                final int defaultButtonIndex,
                                final int focusedButtonIndex,
                                @Nullable final Icon icon,
                                @Nullable final DialogWrapper.DoNotAskOption dontAskOption) {
    return dialogManager().showMessageDialog(description, title, options, defaultButtonIndex, focusedButtonIndex, icon, dontAskOption);
  }

  public static int showOkCancelDialog(@NotNull Project project,
                                       @NotNull String message,
                                       @NotNull String title,
                                       @NotNull String okButtonText,
                                       @NotNull String cancelButtonText,
                                       @Nullable Icon icon) {
    return dialogManager().showMessageDialog(project, message, title, new String[]{okButtonText, cancelButtonText}, 0, icon);
  }

  public static int showYesNoCancelDialog(@NotNull Project project,
                                          @NotNull String message,
                                          @NotNull String title,
                                          @NotNull String yesButtonText,
                                          @NotNull String noButtonText,
                                          @NotNull String cancelButtonText,
                                          @Nullable Icon icon) {
    return dialogManager().showMessageDialog(project, message, title, new String[]{yesButtonText, noButtonText, cancelButtonText}, 0, icon);
  }

  protected void showDialog(@NotNull DialogWrapper dialog) {
    dialog.show();
  }

  protected int showMessageDialog(@NotNull Project project,
                                  @NotNull String message,
                                  @NotNull String title,
                                  @NotNull String[] options,
                                  int defaultButtonIndex,
                                  @Nullable Icon icon) {
    return Messages.showDialog(project, message, title, options, defaultButtonIndex, icon);
  }

  protected int showMessageDialog(@NotNull String description,
                                  @NotNull String title,
                                  @NotNull String[] options,
                                  int defaultButtonIndex,
                                  int focusedButtonIndex,
                                  @Nullable Icon icon,
                                  @Nullable DialogWrapper.DoNotAskOption dontAskOption) {
    return Messages.showDialog(description, title, options, defaultButtonIndex, focusedButtonIndex, icon, dontAskOption);
  }

  @NotNull
  private static DialogManager dialogManager() {
    return ServiceManager.getService(DialogManager.class);
  }
}
