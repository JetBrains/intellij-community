package com.siyeh.igtest.bugs;

public class AssignmentToNullInspection
{
    private Object m_foo = null;

    public static void main(String[] args)
    {
        new AssignmentToNullInspection(new Object()).bar();
    }

    public AssignmentToNullInspection(Object foo)
    {
        m_foo = foo;
    }

    public void bar()
    {
        Object foo = new Object();
        System.out.println("foo = " + foo);
        foo = null;
        m_foo = null;
        System.out.println("foo = " + foo);
        System.out.println("m_foo = " + m_foo);
    }
}