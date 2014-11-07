package com.siyeh.igtest.bugs.var_arg;

import java.lang.invoke.MethodHandle;

public class PrimitiveArrayArgumentToVariableArgMethod
{
    public static void main(String[] arg) throws Throwable
    {
        methodVarArgObject(<warning descr="Confusing primitive array argument to var-arg method">new byte[3]</warning>);
        methodVarArgByteArray(new byte[3]);
        MethodHandle meh = null;
        meh.invokeExact(new int[] { });
    }

    private static void methodVarArgObject(Object... bytes)
    {
    }

    private static void methodVarArgByteArray(byte[]... bytes)
    {
    }

    class X<T> {
        void method(T... t) {
        }
    }

    void foo(byte[] bs) {
        final X<byte[]> x = new X<byte[]>();
        x.method(bs);
    }
}