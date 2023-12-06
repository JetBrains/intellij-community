// "Change type arguments to <*>" "true"
fun test(a: Any) {
    (a as List<Boolean><caret>).bar()
}

fun List<*>.bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix