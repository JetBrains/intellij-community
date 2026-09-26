package foo

private val bar = "bar"

fun test() {
    bar.<caret>
}

// INVOCATION_COUNT: 0
// EXIST: length