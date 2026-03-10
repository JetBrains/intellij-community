// "Add non-null asserted (a!!) call" "true"
// K2_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Foo?'.

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