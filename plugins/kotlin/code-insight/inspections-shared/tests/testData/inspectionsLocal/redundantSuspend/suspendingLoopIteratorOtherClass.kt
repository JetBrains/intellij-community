// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// PROBLEM: none

class OtherIterator {
    suspend operator fun hasNext(): Boolean = false
    suspend operator fun next(): Int = 0
}

class SIterable {
    operator fun iterator() = OtherIterator()
}

<caret>suspend fun foo() {
    val iterable = SIterable()
    for (x in iterable) {
    }
}