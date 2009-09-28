package com.siyeh.igtest.bugs.var_arg;

public class PrimitiveArrayArgumnetToVariableArgMethod
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
}