class Outer {
    inner class <caret>Inner {
        fun get() = 1
    }
}

fun use(outer: Outer): () -> Any = outer::Inner
