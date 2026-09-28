// "Add return keyword" "false"
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun answer(): Int = 42

fun test(): String {
    if (true) {
        <caret>answer()
    }
    return "Done"
}
