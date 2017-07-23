import com.intellij.openapi.actionSystem.*;
public class UniqueToolbarIdTestDataClass {
  public void foo() {
    ActionManager.getInstance().createActionToolbar("asdasd", new ActionGroup(), false);
    ActionManager.getInstance().createActionToolbar(<warning descr="Specify unique toolbar id">""</warning>, new ActionGroup(), false);
    ActionManager.getInstance().createActionToolbar(<warning descr="Specify unique toolbar id">ActionPlaces.UNKNOWN</warning>, new ActionGroup(), false);
  }
}