// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

sealed class MyResult {
    class Ok : MyResult()
    class Err : MyResult()
}

fun take(color: MyResult) {}

fun usage(c: MyResult) {
    if (c is Ok) {}

    when (c) {
        is Ok -> {}
        is Err -> {}
    }
}
