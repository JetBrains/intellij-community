// "Create member function 'Foo.get'" "true"

class Foo<T> {
    fun <S> x (y: Foo<Iterable<S>>) {
        val z: Iterable<S> = y<caret>[""]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix