// DISABLE_ERRORS
fun <caret>main(args: Array<String>): Int {
    return 1
}

// For K2, the return type is highlighted instead of the main name
// IGNORE_K2