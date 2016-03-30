package com.foo.baz;

import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;

public class A {

    public static void foo(@ClosureParams(value = FromString.class, options = "BigInteger") Closure c) {}
    public static void bar(@ClosureParams(value = FromString.class, options = "MyClass") Closure c) {}
}
