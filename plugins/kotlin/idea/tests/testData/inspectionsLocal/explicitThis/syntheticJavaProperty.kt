// WITH_STDLIB

fun test1() {
    Foo().apply {
        <caret>this.s = ""
    }
}