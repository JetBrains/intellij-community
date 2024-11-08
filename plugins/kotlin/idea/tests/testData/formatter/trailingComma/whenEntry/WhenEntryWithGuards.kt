fun foo(a: Any, b: Boolean) {
    when (a) {
        is String if a.isNotEmpty()
            -> Unit
        is String if a.isNotEmpty() && b
            -> Unit
        is String if a.isNotEmpty() && b // dissalowed for guards
            -> Unit
        is String if a.isNotEmpty() && b /* dissalowed for guards */
            -> Unit
        is String if a.isNotEmpty() && b /* dissalowed for guards */ // disallowed for guards
            -> Unit
        is String // if a.isNotEmpty() && b allowed for commented guards
            -> Unit
        is Int
            -> Unit
        else
            -> Unit
    }
}

// SET_TRUE: ALLOW_TRAILING_COMMA
