import com.intellij.openapi.actionSystem.AnAction;

public class <warning descr="Action is not registered in plugin.xml">Unregistered<caret>Action</warning>
  extends AnAction {

  public static class <warning descr="Action is not registered in plugin.xml">InnerAction</warning>
  extends AnAction {}


  protected static class NonPublicIsIgnored extends AnAction {}

}