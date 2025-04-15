// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(param: Any, flag: Boolean) {
    when (param) {
        is Int if <caret>(param < 0 && flag) || (param > 10 && flag) -> println("foo")
        else -> println("bar")
    }
}
