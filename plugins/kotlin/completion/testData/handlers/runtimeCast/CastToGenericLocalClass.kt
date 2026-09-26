// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun main() {
    class LocalBox<T>(val value: T) {
        fun unwrap(): T = value
    }

    val box: Any = LocalBox("hello")
    <caret>val a = 1
}

// ELEMENT: unwrap
// RUNTIME_TYPE: LocalBox
// AUTOCOMPLETE_SETTING: true
