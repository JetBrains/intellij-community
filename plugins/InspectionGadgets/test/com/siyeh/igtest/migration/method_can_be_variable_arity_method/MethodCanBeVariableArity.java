package com.siyeh.igtest.migration.method_can_be_variable_arity_method;

import java.io.IOException;
import java.io.Reader;

public class MethodCanBeVariableArity {

    public void method(String... s) {}

    public void <warning descr="'convertMe()' can be converted to varargs method">convertMe</warning>(String[] ss) {}

    public void convertMeNot(byte[] bs) {}
}
abstract class MyInputStream extends Reader {

  @Override
  public int read(char[] cbuf) throws IOException {
    return super.read(cbuf);
  }
}
class Sub extends MethodCanBeVariableArity {

  @Override
  public void convertMe(String[] ss) {
    super.convertMe(ss);
  }
}
class Annotated {
  public void nullable(@org.jetbrains.annotations.Nullable String[] ss) {}

  void m(String[] ss) {}
}
interface X {
  void <warning descr="'m()' can be converted to varargs method">m</warning>(String[] ss);
}
class Yes {
  void m(int[] is, int[] js) {}
}