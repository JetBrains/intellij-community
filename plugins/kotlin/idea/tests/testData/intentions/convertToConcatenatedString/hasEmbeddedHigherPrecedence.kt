fun foo(): Int? {
    return null
}

fun test(): String {
    return "<caret>as Int: ${foo() as Int}"
}