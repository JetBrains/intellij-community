// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main(args: Array<String>) {
    val b: Base = Derived()
    <caret>val a = 1
}

open class Base {
    fun funInBase() {}

    open fun funWithOverride() { }
    open fun funWithoutOverride() { }

    fun funInBoth() { }
}

open class Intermediate : Base() {
    fun funInIntermediate(){}
}

class Derived : Intermediate() {
    fun funInDerived() { }

    override fun funWithOverride() { }

    fun funInBoth(p: Int) { }
}

// INVOCATION_COUNT: 1
// EXIST: { itemText: "funInBase", tailText: "()", attributes: "bold" }
// EXIST: { itemText: "funWithOverride", tailText: "()", attributes: "bold" }
// EXIST: { itemText: "funWithoutOverride", tailText: "()", attributes: "bold" }
// EXIST: { itemText: "funInDerived", tailText: "()", attributes: "grayed" }
// EXIST: { itemText: "funInBoth", tailText: "()", attributes: "bold" }
// EXIST: { itemText: "funInBoth", tailText: "(p: Int)", attributes: "grayed" }
// EXIST: { itemText: "funInIntermediate", tailText: "()", attributes: "grayed" }
// NOTHING_ELSE


// RUNTIME_TYPE: Derived
// IGNORE_K1