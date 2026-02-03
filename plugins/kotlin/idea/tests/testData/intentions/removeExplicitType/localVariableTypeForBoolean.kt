fun foo(z: Long): Boolean {
    val x: <caret>Boolean = z == 1L
    return x
}