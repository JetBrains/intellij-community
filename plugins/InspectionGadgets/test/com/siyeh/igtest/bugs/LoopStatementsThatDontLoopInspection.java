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

    public static boolean isContainingHash( final String s )
    {
        final char[] c = s.toCharArray();

        for( int i = 0; i < c.length; i++ )
        {
            switch( c[i] )
            {
                case '#':
                    return false;
            }
        }

        return true;
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
