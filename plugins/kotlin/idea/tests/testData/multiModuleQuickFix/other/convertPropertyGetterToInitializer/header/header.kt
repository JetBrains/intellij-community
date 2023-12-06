// "Convert property getter to initializer" "false"
// ERROR: Expected declaration must not have a body
// ACTION: Convert to block body
// IGNORE_K2

expect class C {
    val p: Int
        <caret>get() = 1
}