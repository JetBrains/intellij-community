// WITH_STDLIB
// PROBLEM: none
// ERROR: Cannot check for instance of erased type: () -> Int
// K2_ERROR: CANNOT_CHECK_FOR_ERASED

fun foo(arg: Any?){
    val code = <caret>if (arg is Function0<Int>) {
        arg()
    } else 0
}