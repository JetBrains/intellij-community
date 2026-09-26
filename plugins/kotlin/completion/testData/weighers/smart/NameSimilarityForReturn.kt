// IGNORE_K2
val vFooBar = ""

fun foo(s: String): String {
    return <caret>
}

// ORDER: vFooBar, s, foo
