package foo.bar

enum class E {
    A, B, C;

    companion object {
        fun baz() {}

        val b = <caret>foo.bar.E.Companion::baz
    }
}
