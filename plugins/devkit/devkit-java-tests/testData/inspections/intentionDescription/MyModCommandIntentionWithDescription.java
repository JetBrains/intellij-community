import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;

public class MyModCommandIntentionWithDescription implements ModCommandAction {
  @Override
  public Presentation getPresentation(ActionContext context) {
    return null;
  }

  @Override
  public ModCommand perform(ActionContext context) {
    return null;
  }

  @Override
  public String getFamilyName() {
    return "";
  }
}