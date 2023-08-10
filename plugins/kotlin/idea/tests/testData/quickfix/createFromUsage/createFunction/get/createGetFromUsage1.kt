// "Create member function 'Foo.get'" "true"

class Foo<T> {
    fun x (y: Foo<Iterable<T>>) {
        val z: Iterable<T> = y<caret>[""]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix