// "Change type to MutableList" "true"
// WITH_STDLIB
fun main() {
    val list: List<Int> = java.util.LinkedList()
    list[1]<caret> = 10
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix