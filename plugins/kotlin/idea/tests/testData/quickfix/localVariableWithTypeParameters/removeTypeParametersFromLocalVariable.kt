// "Remove type parameters" "true"

fun test() {
    val <caret><T : unresovled_reference, K> x = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix