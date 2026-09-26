// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:+ExplicitContextArguments


context(ctxB: String, ctxA: Int)
fun foo(valueB: Long, valueA: Double) {}

fun test() {
    foo(
        <caret>
    )
}

// WITH_ORDER
// EXIST: { itemText: "valueB =", tailText: " Long" }
// EXIST: { itemText: "valueA =", tailText: " Double" }
// EXIST: { itemText: "ctxB =", tailText: " String" }
// EXIST: { itemText: "ctxA =", tailText: " Int" }