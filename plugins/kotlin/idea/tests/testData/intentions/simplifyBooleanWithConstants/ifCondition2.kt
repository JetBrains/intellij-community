fun foo() {
    if (<caret>"".length == 0) {
        bar(1)
    } else {
        bar(2)
    }
}

fun bar(i: Int) = i