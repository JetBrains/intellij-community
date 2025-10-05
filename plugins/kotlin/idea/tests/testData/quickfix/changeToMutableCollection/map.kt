// "Change type to MutableMap" "true"
// WITH_STDLIB
fun main() {
    val map = mapOf(1 to "a")
    map[2<caret>] = "b"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToMutableCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix