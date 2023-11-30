import com.intellij.openapi.actionSystem.AnAction;

class A extends B {
  <warning descr="Action presentation instantiated in the constructor">A</warning>() {
    this(42);
  }

  <warning descr="Action presentation instantiated in the constructor">A</warning>(int i) {
  }
}

class B extends AnAction {
  B() {
    super("Test action text");
  }
}