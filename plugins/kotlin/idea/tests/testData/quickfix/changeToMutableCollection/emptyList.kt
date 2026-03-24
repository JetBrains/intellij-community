// "Change type to MutableList" "true"
// WITH_STDLIB
// K2_ERROR: No 'set' operator method providing array access.
fun main() {
    val list = emptyList<Int>()
    list[1]<caret> = 10
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix