package com.foo.baz;

import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;
import groovy.lang.Closure;

public class A {
  public static void foo(@ClosureParams(value = FromString.class, options = "MyClass") Closure c) {}
}