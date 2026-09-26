// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// WITH_STDLIB
// PROBLEM: Duplicate element in collection: '1'
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// FIX: none

fun a() {
    mapOf(<caret>1 to 1, 1 to 2, 3 to 3)
    setOf(1, "1", 3, 4, 5)
}