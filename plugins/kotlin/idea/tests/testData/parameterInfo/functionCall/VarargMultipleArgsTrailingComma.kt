fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    m(true, 1, 2,<caret>)
}
