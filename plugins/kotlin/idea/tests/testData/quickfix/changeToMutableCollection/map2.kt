// "Change type to MutableMap" "true"
// WITH_STDLIB
fun main() {
    val map = foo()
    map[2<caret>] = "b"
}

fun foo() = mapOf(1 to "a")
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToMutableCollectionFix