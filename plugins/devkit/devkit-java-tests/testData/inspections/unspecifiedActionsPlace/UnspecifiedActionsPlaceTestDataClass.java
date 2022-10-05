import com.intellij.openapi.actionSystem.*;
public class UnspecifiedActionsPlaceTestDataClass {
  public void foo() {
    ActionManager actionManager = ActionManager.getInstance();
    // popup menu:
    actionManager.createActionPopupMenu("asdasd", new ActionGroup());
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">""</warning>, new ActionGroup());
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">"unknown"</warning>, new ActionGroup());
    actionManager.createActionPopupMenu(<warning descr="Unspecified place for action popup menu">ActionPlaces.UNKNOWN</warning>, new ActionGroup());
    // toolbar:
    actionManager.createActionToolbar("asdasd", new ActionGroup(), false);
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">""</warning>, new ActionGroup(), false);
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">"unknown"</warning>, new ActionGroup(), false);
    actionManager.createActionToolbar(<warning descr="Unspecified place for action toolbar">ActionPlaces.UNKNOWN</warning>, new ActionGroup(), false);
  }
}
