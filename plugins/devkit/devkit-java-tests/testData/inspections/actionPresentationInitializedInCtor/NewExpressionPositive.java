import com.intellij.openapi.actionSystem.AnAction;

class A extends B {
  <warning descr="Action presentation initialized in the constructor">A</warning>() {
    new B(42);
  }
}

class B extends AnAction {
  B() {
    super("Test action text");
  }

  B(int i) {
    super();
  }
}