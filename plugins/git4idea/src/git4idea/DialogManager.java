package git4idea;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

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

  protected void showDialog(@NotNull DialogWrapper dialog) {
    dialog.show();
  }

}
