// COMPILER_ARGUMENTS: -Xwhen-guards

fun test(param: Any?, a: Boolean?, b: Boolean?, c: Boolean) {
    when (param) {
        is Boolean? if param ?: (a ?: b == true) == true || c<caret> -> println("foo")
        else -> println("bar")
    }
}
