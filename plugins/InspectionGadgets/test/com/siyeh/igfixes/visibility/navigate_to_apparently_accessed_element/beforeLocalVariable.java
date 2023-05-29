// "Navigate to apparently accessed local variable" "true-preview"
public class AmbiguousFieldAccess {
}
class Foo { protected String name;  public void set(String s){} }
class Bar {

  public void set(String s) {}

  private String name;
  void foo(java.util.List<String> name) {
    for(String name1: name) {
      doSome(new Foo() {{
        set(name);
      }});
    }
  }

  void foo() {
    String name = "name";
    new Foo() {{
      System.out.println(na<caret>me);
    }};
  }

  void bar() {
    new Foo() {
      void foo() {
        System.out.println(name);
      }
    };
  }

  private void doSome(Foo foo) {
  }
}