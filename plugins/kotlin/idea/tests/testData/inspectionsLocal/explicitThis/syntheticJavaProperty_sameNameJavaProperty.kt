// WITH_STDLIB

fun test() {
    Foo().apply {
        <caret>this.isB = true
    }
}
