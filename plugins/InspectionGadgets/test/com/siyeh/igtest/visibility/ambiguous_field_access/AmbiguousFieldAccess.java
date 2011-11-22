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
        set(name);
      }});
    }
  }

  private void doSome(Foo foo) {
  }
}