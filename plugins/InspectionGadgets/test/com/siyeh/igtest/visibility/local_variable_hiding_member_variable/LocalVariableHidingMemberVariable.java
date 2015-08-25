package com.siyeh.igtest.visibility.local_variable_hiding_member_variable;

import com.siyeh.igtest.visibility2.DifferentPackageClass;

import java.util.List;


public class LocalVariableHidingMemberVariable extends DifferentPackageClass
{
    private int m_barangus = -1;

    public LocalVariableHidingMemberVariable(int barangus)
    {
        m_barangus = barangus;
    }

    public void foo() {
        int fooBar;
        int <warning descr="Local variable 'fooBar2' hides field in class 'LocalVariableHidingMemberVariable'">fooBar2</warning>;
        final Object <warning descr="Local variable 'm_barangus' hides field in class 'LocalVariableHidingMemberVariable'">m_barangus</warning> = new Object();
        System.out.println("bar" + m_barangus);
    }

    public void setBarangus(int barangus)
    {
        m_barangus = barangus;
        System.out.println(m_barangus);
    }

    public void innerContainer() {
        new Object() {
            void foo() {
                Object <warning descr="Local variable 'm_barangus' hides field in class 'LocalVariableHidingMemberVariable'">m_barangus</warning> = new Object();
            }
        };
    }

    public void foreach(List<String> list) {
      for (String <warning descr="Local variable 'm_barangus' hides field in class 'LocalVariableHidingMemberVariable'">m_barangus</warning> : list) {

      }
    }

    {
        final Object <warning descr="Local variable 'm_barangus' hides field in class 'LocalVariableHidingMemberVariable'">m_barangus</warning> = new Object();
    }

    static {
        final Object m_barangus = new Object();
    }

    static void silentForeach(List<String> list) {
        for (String m_barangus : list) {}
    }
}
