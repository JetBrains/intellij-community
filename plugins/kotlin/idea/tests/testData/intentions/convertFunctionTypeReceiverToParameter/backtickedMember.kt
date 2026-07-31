class Box(val `bar baz`: Int)

fun foo(): <caret>Box.() -> Int = {
    `bar baz`
}
