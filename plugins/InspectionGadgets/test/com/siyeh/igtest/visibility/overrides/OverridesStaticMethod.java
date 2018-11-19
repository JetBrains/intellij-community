package com.siyeh.igtest.visibility.overrides;

public class OverridesStaticMethod extends Base {
  public void method(int i) { /* overrides non-static */ }
  <error descr="Instance method 'method(String)' in 'com.siyeh.igtest.visibility.overrides.OverridesStaticMethod' cannot override static method 'method(String)' in 'com.siyeh.igtest.visibility.overrides.Base'">public void method(String s)</error> { /* overrides static */ }
  public static void <warning descr="Method 'method2()' tries to override a static method of a superclass">method2</warning>(String s) {
    System.out.println("and now for something completely different");
  }
}

class Base {
  public void method(int i) { }
  public static void method(String s) { }
  public static void method2(String s) { }
}