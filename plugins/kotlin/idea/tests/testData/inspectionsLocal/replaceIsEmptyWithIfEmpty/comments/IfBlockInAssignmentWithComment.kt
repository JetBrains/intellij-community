// IGNORE_K1
fun test(string: String) {
    val a =
        // comment before if
        <caret>if (string.isBlank()) "default"
        // comment before else
        else string
}