// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: Non-canonical modifiers order
class Column

var value = 5

<caret>public companion inline var Column.bar: Int
    @Suppress("unused")
    get() = value
    @Suppress("unused")
    set(newValue) {
        value = newValue
    }

fun example() {
    Column.bar = 6
}
