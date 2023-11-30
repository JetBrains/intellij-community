// "Create member function 'Foo.get'" "true"

import java.util.ArrayList

class Foo<T> {
    fun bar(arg: String) { }
    fun <T, V> x (y: Foo<List<T>>, w: ArrayList<V>) {
        val z = y<caret>["", w]
        bar(z)
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix