/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.igtest.bugs.object_to_string;

import java.io.*;
import org.jetbrains.annotations.NonNls;

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

    static class N {}

    static class NN extends N {
        public String toString() {
            return <warning descr="Call to default 'toString()' on 'super'">super</warning>.toString() + ": NN";
        }
    }

    public void testException(N n, int i) {
        assert i < 10 : "Invalid args: "+<warning descr="Call to default 'toString()' on 'n'">n</warning>+", "+i;
        if(i < 0) {
            throw new IllegalArgumentException("Supplied argument is invalid: "+<warning descr="Call to default 'toString()' on 'n'">n</warning>);
        }
        if(i < 1) {
            throw createException(<warning descr="Call to default 'toString()' on 'n'">n</warning>.toString());
        }
    }

    public RuntimeException createException(String msg) {
        return new RuntimeException(msg);
    }

    public void nonNls(@NonNls String s, String nls) {};

    public void testNonNls(N n) {
        nonNls(<warning descr="Call to default 'toString()' on 'n'">n</warning>+"!", <warning descr="Call to default 'toString()' on 'n'">n</warning>+"!");
    }

    void foo(N n) {
        <warning descr="Call to default 'toString()' on 'n'">n</warning>.toString();
    }

    String bar(N n) {
        return "n: " + <warning descr="Call to default 'toString()' on 'n'">n</warning>  + "a";
    }

    void write(Writer writer) {
        writer.toString();
    }

    abstract static class Parent {
        String foo() {
            return this.toString();
        }
    }

    static class Child extends Parent {
        String foo2() {
            return <warning descr="Call to default 'toString()' on 'super'">super</warning>.toString();
        }
    }
}