// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_IDENTICAL

fun foo(a: Int) {}

fun test() {
    val a = "hello"
    foo(<caret>)
}

// ABSENT: { itemText: "a = a" }
// EXIST: { itemText: "a =", tailText: " Int" }