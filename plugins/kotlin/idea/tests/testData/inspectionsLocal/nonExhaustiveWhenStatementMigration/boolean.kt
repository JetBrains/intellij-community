// LANGUAGE_VERSION: 1.6

fun booleanTest() {
    val x: Boolean = true
    w<caret>hen (x) {
        true -> "true"
    }
}