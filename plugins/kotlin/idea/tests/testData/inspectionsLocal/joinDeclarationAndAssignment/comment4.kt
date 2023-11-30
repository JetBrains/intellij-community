// AFTER-WARNING: Variable 'foo' is never used
fun test() {
    // comment 1
    <caret>val foo: String // comment 2
    // comment 3
    bar()
    foo = ""
}

fun bar() {}
