// "Create member function 'Foo.get'" "true"

class Foo<T>
fun <T> x (y: Foo<List<T>>, w: java.util.ArrayList<T>) {
    val z: Iterable<T> = y<caret>["", w]
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix