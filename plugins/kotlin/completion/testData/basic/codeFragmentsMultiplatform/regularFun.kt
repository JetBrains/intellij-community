// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// PLATFORM: Common
// FILE: common.kt
// MAIN

fun aaabbbccc() = Unit

fun foo() {
    aaa<caret>bbbccc()
}

// INVOCATION_COUNT: 1
// EXIST: aaabbbccc

// PLATFORM: Jvm
// FILE: jvm.kt

actual fun aaabbbccc() = Unit