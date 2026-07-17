// "Change type to MutableList" "true"
// WITH_STDLIB
// K2_ERROR: NO_SET_METHOD
fun main() {
    val list = listOf(1, 2, 3)
    list[1]<caret> = 10
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToMutableCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix