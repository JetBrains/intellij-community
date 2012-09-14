package com.siyeh.igtest.classlayout.protected_member_in_final_class;

final class ProtectedMemberInFinalClass {
  protected String s;

  protected void foo() {}
}
class X {
  void foo() {}
}
final class Y extends X{
  protected void foo() {}
}