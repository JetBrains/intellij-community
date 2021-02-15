package com.siyeh.igtest.performance.method_may_be_static;


public class Java16 {
  void run() {
    r.run();
  }
  
  class Inner {
    void useOuter() {
      run();
    }
    
    void <warning descr="Method 'dontUseOuter()' may be 'static'">dontUseOuter</warning>() {
      System.out.println();
    }
  }
  
  Runnable r = new Runnable() {
    public void run() {
      doSmth();
    }
    
    void <warning descr="Method 'doSmth()' may be 'static'">doSmth</warning>() {
      System.out.println();
    }
  };
}