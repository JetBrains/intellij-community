// WITH_STDLIB
fun String.test(): String {
    return <caret>if (isBlank()) {
        "foo"
    } else {
        this
    }
}