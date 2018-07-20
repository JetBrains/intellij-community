package com.siyeh.igtest.style.unnecessary_constant_array_creation_expression;

public class UnnecessaryConstantArrayCreationExpression {
    private Class[] classes1 = <warning descr="'new Class[]{}' can be replaced with array initializer expression">new Class[]{}</warning>;
    private Class<String>[] classes2 = new Class[]{};
    private Object value = new byte[] { 1, 2, 3 };
    
    {
        var ints = new int[] {1, 2, 3};
    }
}
