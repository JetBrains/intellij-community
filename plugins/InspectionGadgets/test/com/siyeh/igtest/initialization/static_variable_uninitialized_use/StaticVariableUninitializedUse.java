/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.igtest.initialization.static_variable_uninitialized_use;

public class StaticVariableUninitializedUse {
    static Integer i;
    static String s;

    static {
        System.out.println(<warning descr="Static field 'StaticVariableUninitializedUse.s' used before initialization">StaticVariableUninitializedUse.s</warning>);
    }

    public static void main(String[] args) {
        if (s instanceof Object) {}
        if (<warning descr="Static field 'i' used before initialization">i</warning> == 42) {
            System.out.println("Unbelievable");
        }
        System.out.println("only warn once in a method" + i);
    }

    static int foo() {
        return <warning descr="Static field 'i' used before initialization">i</warning>;
    }
}
