// "Change type arguments to <*>" "true"
fun <T> test(list: List<*>): List<T> {
    list as List<T><caret>
    return list as List<T>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix