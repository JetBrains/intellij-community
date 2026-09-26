fun foo(): Int? {
    return null
}

fun test(): String {
    return "<caret>is Int: ${foo() is Int}"
}