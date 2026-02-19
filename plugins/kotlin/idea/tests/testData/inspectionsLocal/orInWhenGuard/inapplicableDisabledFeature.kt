// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:-WhenGuards
// K2_ERROR: The feature "when guards" is disabled

fun test(param: Any, flag: Boolean) {
    when (param) {
        is Int <caret>if param < 0 || flag -> println("foo")
        else -> println("bar")
    }
}
