package com.siyeh.igtest.security.design_for_extension;

class DesignForExtension {


  public boolean <warning descr="Method 'equals()' may be overridden and its functionality ignored">equals</warning>(Object o) {
    return true;
  }

  public final int hashCode() {
    return 0;
  }
}
enum E {
  A, B, C;

  public void bla() {
    if (false) bla();
  }
}