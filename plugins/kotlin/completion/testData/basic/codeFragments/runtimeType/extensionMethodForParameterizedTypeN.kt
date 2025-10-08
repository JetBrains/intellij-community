// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main(args: Array<String>) {
    process(Box<String, Number, Long>())
}

private fun process(f: Base) {
    <caret>println()
}

open class Base

class Box<A, B, C> : Base() {}

class Box<A, B, C> {}

fun Box<*, *, *>.extensionStar() {}

// should be included KTIJ-35532
fun Box<String, Number, Long>.extensionTyped1() {}

fun Box<String, Number, String>.extensionTyped2() {}

// INVOCATION_COUNT: 1
// EXIST: extensionStar
// NOTHING_ELSE

// RUNTIME_TYPE: Box

// IGNORE_K1