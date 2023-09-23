val someString: String = ""
val someInt: Int = 1

fun test(some: String): Int {
    return when (some) {
        so<caret>
    }
}

// ORDER: someString
// ORDER: some
// ORDER: someInt