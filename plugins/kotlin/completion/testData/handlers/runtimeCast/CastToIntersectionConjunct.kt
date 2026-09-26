// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main() {
    val obj: Any = object : I1, I2, AC1() {
        override fun myMethod1() = "hello"
        override fun myMethod2() = "world"
        override fun myAbstractMethod() = "abstractMethod"
    }
    <caret>val a = 1
}

abstract class AC1 {
    abstract fun myAbstractMethod(): String
}

interface I1 {
    fun myMethod1(): String
}

interface I2 {
    fun myMethod2(): String
}

// RUNTIME_TYPE: AC1 & I1 & I2
// AUTOCOMPLETE_SETTING: true
