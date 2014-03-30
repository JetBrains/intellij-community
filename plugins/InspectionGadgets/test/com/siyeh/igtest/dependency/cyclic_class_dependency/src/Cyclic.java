package com.siyeh.igtest.dependency.cyclic_class_dependency.src;

/**
 * @author Bas Leijdekkers
 */
public class Cyclic extends Base {

  Cyclic() {
    new Object() {{
      foo();
    }};
  }

  void foo() {}

}
class Base {
  void a() {
    Top.m();
  }
}
class Top extends Cyclic {
  public static void m() {}
}
interface FiveOClock {
  void m(Coffee c);

}
interface Coffee extends FiveOClock {}
