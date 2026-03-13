// "Add non-null asserted (s!!) call" "true"
// K2_ERROR: Smart cast to 'String' is impossible, because 's' is a property that has an open or custom getter.

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