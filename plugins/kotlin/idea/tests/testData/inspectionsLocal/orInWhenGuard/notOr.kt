// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(param: Any, flag: Boolean) {
    when (param) {
        is Int if param < 0 <caret>&& flag -> println("foo")
        else -> println("bar")
    }
}
