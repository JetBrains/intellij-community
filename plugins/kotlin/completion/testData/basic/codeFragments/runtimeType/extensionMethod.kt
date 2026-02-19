// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main(args: Array<String>) {
    val b: Base = Derived()
    <caret>val a = 1
}

open class Base {
}

class Derived: Base() {
}

fun Derived.funExtDerived() { }
fun Base.funExtBase() { }

class A {}
fun A.extraFun() {}

// INVOCATION_COUNT: 1
// EXIST: funExtBase, funExtDerived
// NOTHING_ELSE


// RUNTIME_TYPE: Derived