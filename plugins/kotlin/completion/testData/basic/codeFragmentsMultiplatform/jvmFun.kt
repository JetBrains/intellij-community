// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// PLATFORM: Common
// FILE: common.kt
// MAIN

fun foo() {
    aaa<caret>bbbccc()
}

// INVOCATION_COUNT: 1
// EXIST: aaabbbccc

// PLATFORM: Jvm
// FILE: jvm.kt

/*
This function only exists in JVM.
The 'breakpoint' and autocompletion are in 'common'.
However, since we're debugging in a JVM context, it is "nice to" see symbols from
the 'jvm' part being autocompleted
*/
fun aaabbbccc() = Unit