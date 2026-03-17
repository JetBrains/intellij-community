// SKIP_ERRORS_BEFORE
// WITH_STDLIB
package test

import test.MyResult.Success
import test.MyResult.Error
// no import for Unknown

sealed class MyResult {
    class Success : MyResult()
    class Error : MyResult()
    class Unknown : MyResult()
}

fun test(e: MyResult) {
    <caret>when (e) {
    }
}