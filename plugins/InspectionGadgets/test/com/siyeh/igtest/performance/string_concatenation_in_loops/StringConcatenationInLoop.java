package com.siyeh.igtest.performance.string_concatenation_in_loops;

public class StringConcatenationInLoop
{
    public StringConcatenationInLoop()
    {
    }

    public String foo()
    {
        String foo = "";
        for(int i = 0; i < 5; i++)
        {
            (foo) = ((foo) <warning descr="String concatenation '+' in loop">+</warning> ("  ") + (i));
            foo <warning descr="String concatenation '+=' in loop">+=</warning> foo <warning descr="String concatenation '+' in loop">+</warning> "  " + i;
            baz( foo <warning descr="String concatenation '+' in loop">+</warning> "  " + i);
            if(bar())
            {
                return baz(("foo" + "bar"));
            }
            if(bar())
            {
                throw new Error("foo" + i);
            }
        }
        System.out.println(foo);
        return foo;
    }

    private boolean bar()
    {
        return true;
    }

    private String baz(String s)
    {
        return s;
    }

    public void oper() {
        final String[] array = new String[] { "a", "a", "a" };
        String s = "asdf";
        final int len =  array.length;
        for (int k = 0; k < len; k++) {
            array[k] <warning descr="String concatenation '+=' in loop">+=</warning> "b";
            s <warning descr="String concatenation '+=' in loop">+=</warning> k;
        }
    }

    void bla() {
        while (true) {
            System.out.println("a" + "b" + "c");
        }
    }
}