package com.siyeh.igtest.classlayout.protected_member_in_final_class;

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