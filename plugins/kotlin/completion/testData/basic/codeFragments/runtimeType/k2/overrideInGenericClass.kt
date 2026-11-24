// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main(args: Array<String>) {
    val b: Base<String> = Derived()
    <caret>val a = 1
}

open class Base<T> {
    open fun funWithOverride(t: T) { }
}

class Derived<T> : Base<T>() {
    override fun funWithOverride(t: T) { }

    fun funInDerived(t: T) { }
}

// INVOCATION_COUNT: 1
// EXIST: { itemText: "funWithOverride", tailText: "(t: String)", attributes: "bold" }
// EXIST: { itemText: "funInDerived", attributes: "grayed" }
// NOTHING_ELSE


// RUNTIME_TYPE: Derived
