private fun foo(parameter: String) {}

fun test() {
    foo(param<caret>)
}

// EXIST: "parameter ="