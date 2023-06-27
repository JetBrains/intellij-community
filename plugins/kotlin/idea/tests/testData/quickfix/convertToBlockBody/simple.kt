// "Convert to block body" "true"
fun foo(): Int = when {
    true -> {
        if (true) <caret>return 1
        bar()
        2
    }
    else -> 3
}

fun bar() {}