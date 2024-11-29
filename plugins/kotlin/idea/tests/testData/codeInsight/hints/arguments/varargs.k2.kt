fun m() {
    foo(/*<# …|[varargs.kt:48]a|: #>*/"1, 2, 3, b = true)
}
fun foo(vararg a: Int, b: Boolean): Int {}