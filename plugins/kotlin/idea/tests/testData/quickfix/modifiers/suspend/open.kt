// "Make bar suspend" "true"

suspend fun foo() {}

open class A {
    open fun bar() {
        <caret>foo()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix