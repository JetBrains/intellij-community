import com.intellij.openapi.actionSystem.AnAction;

class A extends B {}

abstract class B extends AnAction {
  B() {
    super();
  }

  B(int i) {
    super("Test action text");
  }
}
