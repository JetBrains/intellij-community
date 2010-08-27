package com.siyeh.igtest.initialization.field;

import junit.framework.TestCase;

public class InstanceVariableInitialization extends TestCase { // needs junit.jar for testcase to work

    private String javaHome;

    InstanceVariableInitialization() {
        //javaHome = System.getProperty("java.home");
    }

    protected void setUp() throws Exception {
        super.setUp();
    }
}
class InstanceVariableInitializationInspection
{
    private int m_fooBar;
    private int m_fooBard;
    private int m_fooBardo;
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