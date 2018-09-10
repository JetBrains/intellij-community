package com.siyeh.igtest.visibility.overrides;

public class OverridesStaticMethod extends Base {
  public void method(int i) { /* overrides non-static */ }
  <error descr="Instance method 'method(String)' in 'com.siyeh.igtest.visibility.overrides.OverridesStaticMethod' cannot override static method 'method(String)' in 'com.siyeh.igtest.visibility.overrides.Base'">public void <warning descr="Method 'method()' overrides a static method of a superclass">method</warning>(String s)</error> { /* overrides static */ }
}

class Base {
  public void method(int i) { }
  public static void method(String s) { }
}