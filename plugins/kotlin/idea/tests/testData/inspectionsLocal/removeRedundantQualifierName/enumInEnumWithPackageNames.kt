package foo.bar

enum class E {
    A, B, C;

    companion object {
        val values = <caret>foo.bar.E.values()
    }
}
