package com.siyeh.igtest.portability.system_exit;



class SystemExit {

  void foo() {
    System.exit(0);
  }

  public static void main(String[] args) {
    System.exit(1);
  }
}