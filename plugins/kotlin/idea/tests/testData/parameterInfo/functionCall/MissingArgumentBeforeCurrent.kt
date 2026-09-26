fun m(x: Int, y: String, z: Boolean) = 2

fun d() {
    m(1, , <caret>true)
}
