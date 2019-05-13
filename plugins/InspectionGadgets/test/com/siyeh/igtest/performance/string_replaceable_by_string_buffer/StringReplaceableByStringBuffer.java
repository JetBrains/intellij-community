package com.siyeh.igtest.performance.string_replaceable_by_string_buffer;

public class StringReplaceableByStringBuffer {
    public void foo()
    {
        String buffer = "bar";
        buffer += "foo";
        System.out.println(buffer);
    }

    public void foo2()
    {
        String <warning descr="Non-constant 'String buffer' should probably be declared as ''StringBuilder''">buffer</warning> = "bar";
        for (int i = 0; i < 10; i++) {
            buffer += "foo";
        }
        System.out.println(buffer);
    }

    public void foobar()
    {
        String buffer = "bar";
        buffer = buffer + "foo" + buffer;
        System.out.println(buffer);
    }

    public void foobar2()
    {
        String <warning descr="Non-constant 'String buffer' should probably be declared as ''StringBuilder''">buffer</warning> = "bar";
        for (int i = 0; i < 10; i++) {
            buffer = buffer + "foo" + buffer;
        }
        System.out.println(buffer);
    }
    
    public void foobaz()
    {
        final String buffer = "bar";
        System.out.println(buffer);
    }
}
