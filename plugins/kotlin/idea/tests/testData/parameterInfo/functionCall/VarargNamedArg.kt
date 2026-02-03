fun m(x: Boolean, vararg y: Int) = 2

fun d() {
    val a = intArrayOf(1, 2, 3)
    m(y = <caret>a, x = true)
}
