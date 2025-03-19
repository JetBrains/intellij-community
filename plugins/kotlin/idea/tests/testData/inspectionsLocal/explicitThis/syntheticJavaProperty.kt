// WITH_STDLIB

fun test1() {
    Foo().apply {
        <caret>this.s = ""
    }
}
// KTIJ-32432
// IGNORE_K2