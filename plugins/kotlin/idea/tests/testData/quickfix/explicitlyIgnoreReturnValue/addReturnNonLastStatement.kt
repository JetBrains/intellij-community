// "Add return keyword" "false"
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun greeting(): String = "Hello"

fun test(): String {
    if (true) {
        <caret>greeting()
        println("Done")
    }
    return "Done"
}
