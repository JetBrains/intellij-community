// "Add non-null asserted (a!!) call" "true"
// K2_ERROR: UNSAFE_CALL

interface Foo {
    fun bar()
}

open class MyClass {
    open val a: Foo? = null

    fun foo() {
        if (a == null) {
            a<caret>.bar()
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix