package com.siyeh.igtest.bugs.null_argument_to_variable_arg_method;

public class NullArgumentToVariableArgMethod {
    public void foo(String[] ss)
    {
        String.format("%s", <warning descr="Confusing argument 'null', unclear if a varargs or non-varargs call is desired">null</warning>);
        String.format("%d", 1);
        String.format("%s", <warning descr="Confusing argument 'ss', unclear if a varargs or non-varargs call is desired">ss</warning>);
    }
}
