package com.siyeh.igtest.visibility.overrides;

public class OverridesStaticMethod extends Base {
  public void method(int i) { /* overrides non-static */ }
  public void method(String s) { /* overrides static */ }
}

class Base {
  public void method(int i) { }
  public static void method(String s) { }
}