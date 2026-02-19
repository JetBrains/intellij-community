context(String)
fun test() {
    ba<caret>
}

fun String.bar() {}

// EXIST: bar
// IGNORE_K2