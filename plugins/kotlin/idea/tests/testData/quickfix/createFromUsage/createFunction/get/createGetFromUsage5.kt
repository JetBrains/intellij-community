// "Create member function 'Foo.get'" "true"

class Foo<T> {
    fun <T, V> x (y: Foo<Iterable<T>>, w: Iterable<V>) {
        val z: Iterable<T> = y<caret>["", w]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix