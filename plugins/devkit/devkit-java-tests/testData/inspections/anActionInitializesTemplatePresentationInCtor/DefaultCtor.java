import com.intellij.openapi.actionSystem.AnAction;

class <warning descr="Action presentation initialized in the constructor">A</warning> extends B {}

abstract class B extends AnAction {
  B() {
    super("blah blah blah");
  }
}
