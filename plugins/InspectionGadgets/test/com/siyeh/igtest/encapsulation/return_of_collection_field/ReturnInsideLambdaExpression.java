import java.util.Set;

class Test {
  Set field;

  interface I {
    Set m();
  }

  private void foo() {
    I i = () -> {
      return <warning descr="'return' of Collection field 'field'">field</warning>;
    };
  }
}