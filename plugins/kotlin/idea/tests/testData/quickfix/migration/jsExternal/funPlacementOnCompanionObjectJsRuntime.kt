// "Create member function 'A.Companion.foo'" "true"
// JS

external class A {
    companion object
}

fun test() {
    A.<caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix