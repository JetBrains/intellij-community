fun Int.prefixExtension() {}

fun main() {
    prefix<caret>.test()
}

// NOTHING_ELSE