fun foo(s: String, i: Int){ }

fun bar() {
    foo(<caret>)
}

fun getString(p: Int): String = ""

//ELEMENT: getString

// IGNORE_K2
