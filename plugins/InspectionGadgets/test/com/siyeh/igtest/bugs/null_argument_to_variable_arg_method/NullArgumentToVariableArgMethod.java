package com.siyeh.igtest.bugs.null_argument_to_variable_arg_method;

public class NullArgumentToVariableArgMethod {
    public void foo()
    {
        String.format("%s", <warning descr="Confusing 'null' argument to var-arg method">null</warning>);
        String.format("%d", 1);
    }
}
