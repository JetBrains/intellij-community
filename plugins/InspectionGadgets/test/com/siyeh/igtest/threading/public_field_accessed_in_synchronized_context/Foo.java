package com.siyeh.igtest.threading.public_field_accessed_in_synchronized_context;

import java.util.List;

class Bar2 {
  public String field1;
  private String field2;

  public void setField2(String s) {
    field2 = s;
  }
}
public class Foo {
  public Bar2 myBar;
  private List<Bar2> myBars;

  synchronized public void setSingle() {
    myBar.field1 = "bar";
    myBar.setField2("bar");
  }

  synchronized public void setViaList() {
    myBars.iterator().next().field1 = "bar";
    myBars.iterator().next().setField2("bar");
  }
}
