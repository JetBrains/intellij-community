import com.intellij.openapi.actionSystem.AnAction;

public class UnregisteredActionUsedViaConstructor
  extends AnAction {

  public static void main(String[] args) {
    AnAction mine = new UnregisteredActionUsedViaConstructor();
  }

}