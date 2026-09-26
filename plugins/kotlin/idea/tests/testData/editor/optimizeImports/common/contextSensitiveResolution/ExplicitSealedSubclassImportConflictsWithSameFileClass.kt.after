// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

import test.Result.Ok

sealed class Result {
    class Ok : Result()
    class Failure : Result()
}

class Ok

fun take(result: Result?) {}

fun usage(result: Result) {
    take(result as? Ok)

    if (result is Ok) {}

    when (result) {
        is Ok -> {}
        is Result.Failure -> {}
    }
}

