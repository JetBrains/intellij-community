// "Create member function 'Foo.iterator'" "true"
class Foo<T>
fun foo() {
    for (i: Int in Foo<caret><Int>()) { }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix