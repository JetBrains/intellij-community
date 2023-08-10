// "Create member function 'Foo.get'" "true"

import java.util.ArrayList

class Foo<T> {
    fun <T, V> x (y: Foo<List<T>>, w: ArrayList<V>, v: T) {
        val z: Iterable<T> = y<caret>["", w, v]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix