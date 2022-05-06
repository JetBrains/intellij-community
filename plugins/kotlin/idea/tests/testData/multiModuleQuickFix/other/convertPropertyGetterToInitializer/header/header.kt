// "Convert property getter to initializer" "false"
// ERROR: Expected declaration must not have a body
// ACTION: Convert to block body
// ACTION: Do not show return expression hints
expect class C {
    val p: Int
        <caret>get() = 1
}