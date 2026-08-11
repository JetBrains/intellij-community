// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// PROBLEM: none
fun test() {
    val x = when {
        <caret>false -> 1
        else -> 2
    }
}