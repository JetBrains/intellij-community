// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
var my = 0
    get() = field
    set(arg) {
        field = arg + 1
    }

fun foo(): Int {
    val field = my
    return field
}