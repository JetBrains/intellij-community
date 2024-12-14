// WITH_STDLIB
// PROBLEM: none

fun foo(arg: Any?){
    val code = <caret>if (arg is Function0<Int>) {
        arg()
    } else 0
}