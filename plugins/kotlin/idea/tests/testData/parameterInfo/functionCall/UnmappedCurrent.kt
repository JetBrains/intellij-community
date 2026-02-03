fun m(x: Int, y: Boolean) = 2

fun d() {
    m(y = false, <caret>unmapped = false)
}
