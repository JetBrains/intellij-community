fun m(x: Int, y: Boolean) = 2

fun d() {
    m(unresolved, <caret>true)
}
