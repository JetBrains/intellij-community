// "Change type to MutableList" "true"
// WITH_STDLIB
fun main() {
    val list = foo()
    list[1]<caret> = 10
}

fun foo() = listOf(1, 2, 3)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToMutableCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.collections.ChangeToMutableCollectionFix