package com.siyeh.igtest.visibility.parameter_hiding_member_variable;

public class ParameterHidingMemberVariable
{
    private int bar = -1;

    public ParameterHidingMemberVariable(int bar)
    {
        this.bar = bar;
    }

    public void setBar(int bar)
    {
        this.bar = bar;
    }

    public void foo(Object bar)
    {
        System.out.println("bar" + bar);
    }

    private static String x = "hello";

    @Override
    public String toString() {
        new Object() {
            public void foo(final String x) {
                System.out.println(x);
            }
        };
        return x+super.toString();
  }
}
