// "Convert to anonymous object" "true"
interface I {
    fun bar(): Unit
}

fun foo() {
}

fun test() {
    <caret>I {
        foo()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertToAnonymousObjectFix