package com.siyeh.igtest.visibility.parameter_hiding_member_variable;

public class ParameterHidingMemberVariable
{
    private int bar = -1;

    public ParameterHidingMemberVariable(int <warning descr="Parameter 'bar' hides field in class 'ParameterHidingMemberVariable'">bar</warning>)
    {
        this.bar = bar;
    }

    public void setBar(int <warning descr="Parameter 'bar' hides field in class 'ParameterHidingMemberVariable'">bar</warning>)
    {
        this.bar = bar;
    }

    public void foo(Object <warning descr="Parameter 'bar' hides field in class 'ParameterHidingMemberVariable'">bar</warning>)
    {
        System.out.println("bar" + bar);
    }

    private static String x = "hello";

    @Override
    public String toString() {
        new Object() {
            public void foo(final String <warning descr="Parameter 'x' hides field in class 'ParameterHidingMemberVariable'">x</warning>) {
                System.out.println(x);
            }
        };
        return x+super.toString();
    }

    public static void setBar2(int bar) {
        System.out.println(bar);
    }

    int i;

    class X {
        void m(int <warning descr="Parameter 'i' hides field in class 'ParameterHidingMemberVariable'">i</warning>) {}
    }
    static class Y {
        void a(int i) {}
    }
}
