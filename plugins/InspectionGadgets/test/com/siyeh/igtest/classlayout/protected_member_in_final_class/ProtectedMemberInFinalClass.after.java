package com.siyeh.igtest.classlayout.protected_member_in_final_class;
import java.util.List;

final class ProtectedMemberInFinalClass {
  private String s;

  private void foo() {}
}
class X {
  void foo() {}
  protected static void m() {}
}
final class Y extends X{
  protected void foo() {}
  protected static void m() {
    new Z().x();
  }
}
final class Z {

  void x() {}
}

final class Test {
  String s1;
  private String s2;
  private String s3;

  private void foo1(List<? extends Test> f) {
    String s = f.get(0).s1;
    f.get(0).bar1();
  }

  private void foo2(List<? super Test> f) {
    String s = f.get(0).s2;
    f.get(0).bar2();
  }

  private void foo3(List<Test> f) {
    String s = f.get(0).s3;
    f.get(0).bar3();
  }

  void bar1() {
  }

  private void bar2() {
  }

  private void bar3() {
  }
}