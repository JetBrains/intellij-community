import com.intellij.openapi.actionSystem.*;
public class UnspecifiedActionsPlaceTestDataClass {
  public void foo() {
    ActionManager.getInstance().createActionToolbar("asdasd", new ActionGroup(), false);
    ActionManager.getInstance().createActionToolbar(<warning descr="Unspecified place for action toolbar">""</warning>, new ActionGroup(), false);
    ActionManager.getInstance().createActionToolbar(<warning descr="Unspecified place for action toolbar">ActionPlaces.UNKNOWN</warning>, new ActionGroup(), false);
  }
}
