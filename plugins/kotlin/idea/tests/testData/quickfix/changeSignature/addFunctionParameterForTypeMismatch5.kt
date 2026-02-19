// "Add 1st parameter to function 'foo'" "false"
// ACTION: Change parameter 'i1' type of function 'foo' to 'String'
// ACTION: Convert to 'buildString' call
// ACTION: Convert to raw string literal
// ACTION: Create function 'foo'
// ACTION: Do not show hints for current method
// ACTION: Put arguments on separate lines
// DISABLE_ERRORS
fun foo(i1: Int, i2: Int, i3: Int, i4: Int) {
}

fun test() {
    foo(<caret>"", "", 3, 4, 5)
}

// IGNORE_K2