// "Create member function 'Bar.foo'" "true"
fun foo() {
    Bar.BAZ.<caret>foo()
}

enum class Bar {
    BAZ;
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix