package com.siyeh.igtest.bugs;

public class LoopStatementsThatDontLoopInspection
{
    private final int m_foo = 3;

    public static void main(String[] args) throws Exception
    {
        new LoopStatementsThatDontLoopInspection().foo();
    }

    public LoopStatementsThatDontLoopInspection()
    {
    }

    private void foo() throws Exception
    {
        System.out.println("m_foo =" + m_foo);
        for(; ;)
        {
            break;
        }

        while(true)
        {
            break;
        }

        do
        {
            break;
        }
        while(true);

        while(true)
        {
            throw new Exception();
        }

       // for(; ;)
        //{
           // return;
       // }

    }

}
