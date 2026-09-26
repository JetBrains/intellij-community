// FIR_COMPARISON
// FIR_IDENTICAL
package test

sealed class MyResult {
    class Ok(val v: String) : MyResult()
    class Err(val m: String) : MyResult()
}

fun handle(r: MyResult) {
    if (r is O<caret>) {}
}

// ELEMENT: Ok

// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
