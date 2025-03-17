// PROBLEM: none

fun test(param: Any, flag: Boolean) {
    when {
        (param is Int) && param < 0 <caret>|| flag -> println("foo")
        else -> println("bar")
    }
}
