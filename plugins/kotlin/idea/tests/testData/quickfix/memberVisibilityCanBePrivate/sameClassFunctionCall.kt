// "Make 'foo' 'private'" "true"
open class A {
    <caret>internal fun foo() {}

    fun bar(a: A) {
        a.foo()
    }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix