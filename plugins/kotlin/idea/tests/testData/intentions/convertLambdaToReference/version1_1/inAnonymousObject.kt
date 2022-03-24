// LANGUAGE_VERSION: 1.1
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used
interface I {
    fun foo(i: Int)
}

fun create(): I {
    return object : I {
        override fun foo(i: Int) {
            bar {<caret> baz(it) }
        }

        fun bar(f: (Int) -> Unit) {}

        fun baz(i: Int) {}
    }
}
