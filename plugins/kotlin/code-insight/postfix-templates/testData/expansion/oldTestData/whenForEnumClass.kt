enum class E {
    Foo, Bar, Baz
}

fun test(e: E) {
    e.when<caret>
}