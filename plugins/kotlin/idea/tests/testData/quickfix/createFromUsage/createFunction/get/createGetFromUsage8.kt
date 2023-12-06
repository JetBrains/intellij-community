// "Create member function 'Foo.get'" "true"

import java.util.ArrayList

class Foo<T> {
    fun x (y: Foo<List<T>>, w: ArrayList<T>) {
        val z: Iterable<T> = y<caret>["", w]
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateCallableFromUsageFix