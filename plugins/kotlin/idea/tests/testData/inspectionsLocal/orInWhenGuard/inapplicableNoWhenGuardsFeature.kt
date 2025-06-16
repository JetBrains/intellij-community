// PROBLEM: none
// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(param: Any, flag: Boolean) {
    when (param) {
        is Int <caret>if param < 0 || flag -> println("foo")
        else -> println("bar")
    }
}
