package com.siyeh.igtest.bugs.var_arg;

public class PrimitiveArrayArgumentToVariableArgMethod
{
    public static void main(String[] arg)
    {
        methodVarArgObject(new byte[3]);
        methodVarArgByteArray(new byte[3]);
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