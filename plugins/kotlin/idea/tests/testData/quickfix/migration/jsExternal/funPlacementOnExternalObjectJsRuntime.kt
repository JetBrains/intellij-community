// "Create member function 'A.foo'" "true"
// JS

external object A

fun test() {
    A.<caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix