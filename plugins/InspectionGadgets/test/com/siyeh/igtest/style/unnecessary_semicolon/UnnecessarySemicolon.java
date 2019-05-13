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
package com.siyeh.igtest.style.unnecessary_semicolon;<warning descr="Unnecessary semicolon ';'">;</warning>

import java.util.List;<warning descr="Unnecessary semicolon ';'">;</warning>

class UnnecessarySemicolon {
    int i;
    <warning descr="Unnecessary semicolon ';'">;</warning> // comment
    public UnnecessarySemicolon() {
        <warning descr="Unnecessary semicolon ';'">;</warning> // this is a comment
        <warning descr="Unnecessary semicolon ';'">;</warning> /* another */
    }
}  <warning descr="Unnecessary semicolon ';'">;</warning> // comment

enum Status {
    BUSY    ("BUSY"    ),
    DONE    ("DONE"    )  ; // <<---- THIS IS NOT UNNECESSARY

    public final String text;   <warning descr="Unnecessary semicolon ';'">;</warning>
    private Status (String i_text) {
        text = i_text;
    }
}

enum BuildType {
    ;

    public String toString() {
        return super.toString();
    }
}

class C {
    void m() throws Exception {
        try (AutoCloseable r1 = null; AutoCloseable r2 = null /*ok*/ ) { }
        try (AutoCloseable r1 = null; AutoCloseable r2 = null<warning descr="Unnecessary semicolon ';'">;</warning> /*warn*/ ) { }
    }
}