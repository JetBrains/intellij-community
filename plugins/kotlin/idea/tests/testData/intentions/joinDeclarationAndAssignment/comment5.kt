// AFTER-WARNING: Variable 'foo' is never used
fun test() {
    // comment 1
    // comment 2
    <caret>val foo: String // comment 3
    // comment 4
    bar()
    // comment 5
    // comment 6
    foo = "" // comment 7
}

fun bar() {}
