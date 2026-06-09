// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// WITH_STDLIB
// PROBLEM: none
fun foo(list: List<Int>) {
    list.forEach {
        for (i in 1..10) {
            if (i > 5) {
                <caret>return@forEach
            }
        }
        println("done")
    }
}
