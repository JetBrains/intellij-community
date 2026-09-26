// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// WITH_STDLIB
// PROBLEM: Duplicate element in collection: '1'
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// FIX: none

const val b = 1
fun a() {
    setOf(<caret>1, b, 3, 4, 5, 6, 7, 8, 9, 10)
}