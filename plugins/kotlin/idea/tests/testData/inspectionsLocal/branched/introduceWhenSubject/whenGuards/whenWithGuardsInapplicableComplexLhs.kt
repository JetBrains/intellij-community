// COMPILER_ARGUMENTS: -Xwhen-guards
// PROBLEM: none

fun test(a: Number) {
    when<caret> {
        (a is Int || a is Double) && a.toInt() > 5 -> Unit
        else -> Unit
    }
}
