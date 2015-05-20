package com.siyeh.igtest.visibility.ambiguous_field_access;

public class AmbiguousFieldAccess {
}
class Foo { protected String name;  public void set(String s){} }
class Bar {

  public void set(String s) {}

  private String name;
  void foo(java.util.List<String> name) {
    for(String name1: name) {
      doSome(new Foo() {{
        set(<warning descr="Access of field 'name' from superclass 'Foo' looks like access of parameter">name</warning>);
      }});
    }
  }

  void foo() {
    String name = "name";
    new Foo() {{
      System.out.println(<warning descr="Access of field 'name' from superclass 'Foo' looks like access of local variable">name</warning>);
    }};
  }

  void bar() {
    new Foo() {
      void foo() {
        System.out.println(<warning descr="Access of field 'name' from superclass 'Foo' looks like access of field from surrounding class">name</warning>);
      }
    };
  }

  private void doSome(Foo foo) {
  }
}