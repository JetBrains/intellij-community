package com.siyeh.igtest.bugs;

import java.util.List;

public class LoopStatementsThatDontLoop
{
    private final int m_foo = 3;

    public static void main(String[] args) throws Exception
    {
        new LoopStatementsThatDontLoop().foo();
    }

    public LoopStatementsThatDontLoop()
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
        <warning descr="'for' statement does not loop">for</warning>(; ;)
        {
            break;
        }

        <warning descr="'while' statement does not loop">while</warning>(true)
        {
            break;
        }

        <warning descr="'do' statement does not loop">do</warning>
        {
            break;
        }
        while(true);

        <warning descr="'while' statement does not loop">while</warning>(true)
        {
            throw new Exception();
        }

        <error descr="Unreachable statement"><warning descr="'for' statement does not loop">for</warning></error>(; ;)
        {
            return;
        }

    }

    enum Modification {
        NONE, SET, REMOVE;
    }

    boolean foo(List<Modification> list){
        for (Modification modification : list) {
            switch (modification) {
                case SET:
                case REMOVE:
                    return true;
            }
        }
        <warning descr="'for' statement does not loop">for</warning> (Modification modification : list) {
            switch (modification) {
                case SET:
                case REMOVE:
                    return true;
                case NONE:
                    return false;
            }

        }
        return false;
    }

}
