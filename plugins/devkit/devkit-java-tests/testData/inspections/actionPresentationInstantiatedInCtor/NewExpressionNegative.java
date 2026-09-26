import com.intellij.openapi.actionSystem.AnAction;

class A extends B {
  A() {
    new B(42);
  }
}

class B extends AnAction {
  B() {
    super();
  }

  B(int i) {
    super("Test action text");
  }
}