// "Replace ',' with '||' in when" "true"
fun test(a: Boolean, b: Boolean, c: Boolean) {
    val c = when {
        a<caret>, b -> "a"
        c -> "b"
        else -> "e"
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CommaInWhenConditionWithoutArgumentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CommaInWhenConditionWithoutArgumentFix