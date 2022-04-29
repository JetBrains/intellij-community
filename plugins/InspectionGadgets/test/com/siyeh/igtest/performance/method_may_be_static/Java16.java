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
  
  void <warning descr="Method 'foo()' may be 'static'">foo</warning>(int a) {
    int b = 42;
    Runnable r2 = new Runnable() {
      public void run() {
        doSmth1();
        doSmth2();
        doSmth3(b);
      }
      
      void doSmth1() {
        System.out.println(a);
      }
      
      void doSmth2() {
        System.out.println(b);
      }

      void <warning descr="Method 'doSmth3()' may be 'static'">doSmth3</warning>(int c) {
        System.out.println(c);
      }
    };
  }
}