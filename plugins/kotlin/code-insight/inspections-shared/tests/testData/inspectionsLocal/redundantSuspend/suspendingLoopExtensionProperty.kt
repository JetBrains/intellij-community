// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// PROBLEM: none

class SIterable {
}

operator fun SIterable.iterator() = this
suspend operator fun SIterable.hasNext(): Boolean = false
suspend operator fun SIterable.next(): Int = 0

<caret>suspend fun foo() {
    val iterable = SIterable()
    for (x in iterable) {
    }
}