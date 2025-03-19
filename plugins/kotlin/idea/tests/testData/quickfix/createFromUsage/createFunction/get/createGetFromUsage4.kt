// "Create member function 'Foo.get'" "true"

class Foo<T, S: Iterable<T>> {
    fun <U> x (y: Foo<U, Iterable<U>>) {
        val z: U = y<caret>[""]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix