// "Change type to MutableMap" "true"
// WITH_STDLIB
// K2_ERROR: No 'set' operator method providing array access.
fun main() {
    val map = foo()
    map[2<caret>] = "b"
}

fun foo() = mapOf(1 to "a")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToMutableCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix