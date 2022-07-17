package com.siyeh.igtest.initialization.field;

import <error descr="Cannot resolve symbol 'junit'">junit</error>.framework.TestCase;

public class InstanceVariableInitialization extends <error descr="Cannot resolve symbol 'TestCase'">TestCase</error> { // needs junit.jar for testcase to work

    private String <warning descr="Instance field 'javaHome' may not be initialized during object construction">javaHome</warning>;

    InstanceVariableInitialization() {
        //javaHome = System.getProperty("java.home");
    }

    protected void setUp() throws Exception {
        super.<error descr="Cannot resolve method 'setUp()'">setUp</error>();
    }
}
class InstanceVariableInitializationInspection
{
    private int m_fooBar;
    private int m_fooBard;
    private int <warning descr="Instance field 'm_fooBardo' may not be initialized during object construction">m_fooBardo</warning>;
    private int m_fooBaz = 1;
    private int m_fooBarangus;

    {
        m_fooBarangus = 2;
    }

    public InstanceVariableInitializationInspection()
    {
        this(3);
    }

    public InstanceVariableInitializationInspection(int fooBard)
    {
        if(barangus())
        {
            m_fooBard = fooBard;
            m_fooBardo = fooBard;
        }
        else
        {
            m_fooBard = fooBard + 1;
        }
    }

    private boolean barangus()
    {
        m_fooBar = 3;
        return false;
    }

    public void dump()
    {
        System.out.println("m_fooBar = " + m_fooBar);
        System.out.println("m_fooBarangus =     " + m_fooBarangus);
        System.out.println("m_fooBard = " + m_fooBard);
        System.out.println("m_fooBardo = " + m_fooBardo);
        System.out.println("m_fooBaz = " + m_fooBaz);
    }
}

class Test {
    private int var;

    Test(boolean flag) {
        if (flag) {
            var = 77;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
class A {
  private int n=k=0;
  private int k; // instance variable 'k' may not be initialized during object construction (false positive)

  private String s = t = "";
  private String t;
}
class B {
  private int i;

  B() throws java.io.FileNotFoundException {
    try (<error descr="Unhandled exception from auto-closeable resource: java.io.IOException">java.io.FileInputStream in = new java.io.FileInputStream("asdf" + (i = 3) + "asdf")</error>) {

    }
  }
}
class C {
  private Object o;

  C() {
    boolean b = (o = "") instanceof String;
  }
}
class D {
  private java.util.List l;
  D() {
    for (Object o : l = new java.util.ArrayList()) {}
  }
}
class WithNullable {
  @org.jetbrains.annotations.Nullable
  private String str;
}

class JUnit5Test {

  private String <warning descr="Instance field 'myField' may not be initialized during object construction">myField</warning>;

  @org.<error descr="Cannot resolve symbol 'junit'">junit</error>.jupiter.api.BeforeEach
  void init() {
    myField = "";
  }

  @org.<error descr="Cannot resolve symbol 'junit'">junit</error>.jupiter.api.Test
  void checkJUnit5() {
    System.out.println(myField);
  }
}