// "Change type to MutableList" "true"
// WITH_STDLIB
fun main() {
    val list = listOf(1, 2, 3)
    list[1]<caret> = 10
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToMutableCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix