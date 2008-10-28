package com.siyeh.igtest.naming.exception_name_doesnt_end_with_exception;

public class ExceptionNameDoesntEndWithException {

    public <E extends Exception> void method() { }

    class X extends IllegalArgumentException {}
}