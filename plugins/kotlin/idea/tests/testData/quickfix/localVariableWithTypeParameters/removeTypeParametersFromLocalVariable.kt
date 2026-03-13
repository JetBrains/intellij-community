// "Remove type parameters" "true"
// K2_ERROR: Local variables cannot have type parameters.
// K2_ERROR: Unresolved reference 'unresovled_reference'.

fun test() {
    val <caret><T : unresovled_reference, K> x = 0
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemovePsiElementSimpleFix