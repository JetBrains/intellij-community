// WITH_STDLIB

fun test() {
    Foo().apply {
        <caret>this.isB = true
    }
}
// KTIJ-32432
// IGNORE_K2