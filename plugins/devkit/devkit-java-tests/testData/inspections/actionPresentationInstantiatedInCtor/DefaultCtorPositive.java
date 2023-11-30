import com.intellij.openapi.actionSystem.AnAction;

class <warning descr="Action presentation instantiated in the constructor">A</warning> extends B {}

abstract class B extends AnAction {
  B() {
    super("Test action text");
  }

  B(int i) {
    super();
  }
}
