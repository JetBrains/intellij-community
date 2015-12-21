package com.siyeh.igtest.bugs.object_to_string;

import java.io.*;

class ObjectToString<E>
{
    E item;
    public String toString() {
        return item.toString();
    }

    public static void main(String[] args) {
        String pwd = "password";
        char[] pwdCharArray = pwd.toCharArray();
        String t = String.valueOf(pwdCharArray);
        // The inspection "Call to default 'toString()'" marks "pwdCharArray" although String.valueOf() can handle char[]
    }

    class N {}

    void foo(N n) {
        <warning descr="Call to default 'toString()' on 'n'">n</warning>.toString();
    }

    String bar(N n) {
        return "n: " + <warning descr="Call to default 'toString()' on 'n'">n</warning>  + "a";
    }

    void write(Writer writer) {
        writer.toString();
    }
}