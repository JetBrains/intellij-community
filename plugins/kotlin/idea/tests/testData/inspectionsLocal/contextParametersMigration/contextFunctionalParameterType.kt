// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class C

fun foo(
    bar: context(<caret>C) () -> Unit
) {}
