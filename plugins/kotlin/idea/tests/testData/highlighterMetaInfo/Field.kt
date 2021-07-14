// FIR_IDENTICAL
var my = 0
    get() = field
    set(arg) {
        field = arg + 1
    }

fun foo(): Int {
    val field = my
    return field
}