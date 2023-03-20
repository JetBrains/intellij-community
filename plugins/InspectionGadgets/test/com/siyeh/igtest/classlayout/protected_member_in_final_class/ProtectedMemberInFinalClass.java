package com.siyeh.igtest.classlayout.protected_member_in_final_class;
import java.util.List;

final class ProtectedMemberInFinalClass {
  <warning descr="Class member declared 'protected' in 'final' class"><caret>protected</warning> String s;

  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> void foo() {}
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

  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> void x() {}
}

final class Test {
  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> String s1;
  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> String s2;
  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> String s3;

  private void foo1(List<? extends Test> f) {
    String s = f.get(0).s1;
    f.get(0).bar1();
  }

  private void foo2(List<? super Test> f) {
    String s = f.get(0).<error descr="Cannot resolve symbol 's2'">s2</error>;
    f.get(0).<error descr="Cannot resolve method 'bar2' in 'Object'">bar2</error>();
  }

  private void foo3(List<Test> f) {
    String s = f.get(0).s3;
    f.get(0).bar3();
  }

  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> void bar1() {
  }

  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> void bar2() {
  }

  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> void bar3() {
  }
}