private fun foo(first: Int, second: Int) {}

fun test() {
    foo(first = 10, sec<caret>)
}

// EXIST: "second ="