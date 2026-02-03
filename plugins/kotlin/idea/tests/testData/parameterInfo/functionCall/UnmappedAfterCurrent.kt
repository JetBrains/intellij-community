fun m(x: Int, y: Boolean) = 2

fun d() {
    m(<caret>y = false, unmapped = false)
}
