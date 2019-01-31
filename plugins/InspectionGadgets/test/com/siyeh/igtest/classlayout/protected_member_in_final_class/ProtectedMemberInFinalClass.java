package com.siyeh.igtest.classlayout.protected_member_in_final_class;

final class ProtectedMemberInFinalClass {
  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> String s;

  <warning descr="Class member declared 'protected' in 'final' class">protected</warning> void foo() {}
}
class X {
  void foo() {}
}
final class Y extends X{
  protected void foo() {}
}