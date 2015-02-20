package git4idea;

import com.intellij.openapi.components.ServiceManager;
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
    ServiceManager.getService(DialogManager.class).showDialog(dialog);
  }

  public static int showMessage(@NotNull String description,
                                @NotNull String title,
                                @NotNull String[] options,
                                int defaultButtonIndex,
                                int focusedButtonIndex,
                                @Nullable Icon icon,
                                @Nullable DialogWrapper.DoNotAskOption dontAskOption) {
    return ServiceManager.getService(DialogManager.class).showMessageDialog(description, title, options,
                                                                            defaultButtonIndex, focusedButtonIndex, icon, dontAskOption);
  }

  protected void showDialog(@NotNull DialogWrapper dialog) {
    dialog.show();
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
}
