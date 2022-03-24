// WITH_STDLIB
fun String.test(): String {
    return if (isBlank<caret>()) {
        "foo"
    } else {
        this
    }
}