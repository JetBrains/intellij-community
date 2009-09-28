package com.siyeh.igtest.bugs;

public class AssignmentUsedAsConditionInspection
{
    private final Object m_foo;
    private final boolean m_bar;

    public static void main(String[] args)
    {
        new AssignmentUsedAsConditionInspection(new Object()).fooBar();
    }

    public AssignmentUsedAsConditionInspection(Object foo)
    {
        m_foo = foo;
        m_bar = m_foo == null;
    }

    private void fooBar()
    {
        final boolean[] foo = new boolean[1];
        Runnable runnable = new Runnable()
        {
            public void run()
            {
                if(foo[0] = m_bar)
                {
                    System.out.println("foo = " + foo[0]);
                }
            }
        };
        if(foo[0] = true)
        {
            System.out.println("foo = " + foo[0]);
        }
        if(foo[0] = m_bar)
        {
            System.out.println("foo = " + foo[0]);
        }
    }
}