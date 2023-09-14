val someString = ""

fun test(str: String): Int {
    return when (str) {
        s<caret>
    }
}

// EXIST: someString

