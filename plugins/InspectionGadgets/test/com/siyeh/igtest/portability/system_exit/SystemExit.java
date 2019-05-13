package com.siyeh.igtest.portability.system_exit;



class SystemExit {

  void foo() {
    System.<warning descr="Call to 'System.exit()' is non-portable">exit</warning>(0);
  }

  public static void main(String[] args) {
    System.exit(1);
  }
}