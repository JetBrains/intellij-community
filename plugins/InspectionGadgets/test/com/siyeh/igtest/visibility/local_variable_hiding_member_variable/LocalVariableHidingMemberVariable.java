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
class Outer {
    String field;

    void localHidesField() {
        // "Local variable hides field" was designed to detect this
        String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
        System.out.println(field);
    }
    class Inner {
        void innerLocalHidesOuterField() {
            // "Local variable hides field" was designed to detect this
            String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
            System.out.println(field);
        }
    }

    static void localHidesOuterField() {
        // "Ignore local variables in static methods" option toggles this warning
        String field = "hello";
        System.out.println(field);
    }

    static class InnerStatic {
        void staticInnerLocalHidesOuterField() {
            // Invalid warning, because inner class can't access instance method, even if it wanted to.
            String field = "hello";
            System.out.println(field);
        }
    }
    static void staticInnerLocalHidesOuterField() {
        new Runnable() {
            @Override public void run() {
                // Invalid warning, because inner class can't access instance method, even if it wanted to.
                String field = "hello";
                System.out.println(field);
            }
        }.run();
    }

    static void exceptionToTheRule(final Outer outer) {
        // Of course there's an exception to "can't access instance method, even if it wanted to."
        new Runnable() {
            @Override public void run() {
                // Invalid warning, because inner class has to go trough the passed-in named parameter.
                // There's no name shadowing issue in this case, because of forced object access.
                String field = "hello";
                System.out.println(field);
                System.out.println(outer.field);
            }
        }.run();
    }
}