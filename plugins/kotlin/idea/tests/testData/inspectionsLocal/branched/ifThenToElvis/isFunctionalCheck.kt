// WITH_STDLIB
// PROBLEM: none
// ERROR: Cannot check for instance of erased type: () -> Int
// K2_ERROR: Cannot check for instance of erased type 'Function0<Int>'.

fun foo(arg: Any?){
    val code = <caret>if (arg is Function0<Int>) {
        arg()
    } else 0
}