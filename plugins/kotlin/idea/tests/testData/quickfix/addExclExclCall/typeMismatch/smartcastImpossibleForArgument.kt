// "Add non-null asserted (!!) call" "true"

open class MyClass {
    open val s: String? = null

    fun foo() {
        if (s != null) {
            bar(<caret>s)
        }
    }

    fun bar(s: String) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix