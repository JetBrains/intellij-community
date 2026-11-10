// SHOULD_FAIL_WITH: Callable reference won't be processed: outer::Inner

class Outer {
    val x = 1

    inner class <caret>Inner {
        fun get() = x
    }
}

fun use(outer: Outer): () -> Any = outer::Inner
