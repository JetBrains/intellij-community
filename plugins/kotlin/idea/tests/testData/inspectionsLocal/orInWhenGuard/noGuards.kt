// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(param: Any, flag1: Boolean, flag2: Boolean) {
    when {
        (param is Int) && flag1 <caret>|| flag2 -> println("foo")
        else -> println("bar")
    }
}
