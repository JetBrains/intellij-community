package com.siyeh.igtest.performance;

public class
        StringConcatenationInLoopInspection
{
    public StringConcatenationInLoopInspection()
    {
    }

    public String foo()
    {
        String foo = "";
        for(int i = 0; i < 5; i++)
        {
            (foo) = ((foo) + ("  ") + (i));
            foo += foo + "  " + i;
            baz( foo + "  " + i);
            if(bar())
            {
                return baz(("foo" + "bar"));
            }
            if(bar())
            {
                throw new OutOfMemoryError("foo" + i);
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
}