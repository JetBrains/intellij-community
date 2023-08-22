// "Convert to anonymous object" "true"
interface I {
    fun foo(a: String, b: Int): Int
}

fun test() {
    <caret>I {
        1
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix