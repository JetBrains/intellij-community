package com.siyeh.igtest.naming.overloaded_methods_with_same_number_of_parameters;

public class OverloadedMethodsWithSameNumberOfParameters {

    public void foo(int i) {}
    public void foo(String s) {}

    public void equals(String s) {}
}
interface MyInterface {
    void myMethod(String value);
}
class MyClass implements MyInterface {
    @Override
    public void myMethod(String value) {
    }
}