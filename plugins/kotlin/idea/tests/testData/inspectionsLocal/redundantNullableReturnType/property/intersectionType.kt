// PROBLEM: none
fun foo(
    any: Any,
    flag1: Boolean,
    flag2: Boolean,
) {
    val result: Any?<caret> = when {
        flag1 -> any as Int
        flag2 -> any as String
        else -> null
    }
}
