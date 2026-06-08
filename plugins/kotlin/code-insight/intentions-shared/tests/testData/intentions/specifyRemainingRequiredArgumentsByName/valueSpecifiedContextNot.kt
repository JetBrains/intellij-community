// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xcontext-parameters
// COMPILER_ARGUMENTS: -Xexplicit-context-arguments

// IGNORE_K2
// Unmute after KTIJ-39044 is fixed
context(s: String, n: Int)
fun foo(a: Int) {}

fun test() {
    foo(a = 1<caret>)
}