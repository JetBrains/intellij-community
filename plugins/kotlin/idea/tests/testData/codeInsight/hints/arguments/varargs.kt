fun m() {
    foo(<hint text="...a:"/>1, 2, 3, b = true)
}
fun foo(vararg a: Int, b: Boolean): Int {}