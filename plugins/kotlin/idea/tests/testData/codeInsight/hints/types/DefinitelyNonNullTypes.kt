// MODE: all
fun <T> inlayHint(x: T & Any)/*<# : |[inlayHint.T:kotlin.fqn.class]T & Any #>*/ = x

fun <T>takeFun(f: (T & Any) -> Unit) {}
fun <T> inlayHint2()/*<# : |[kotlin.Unit:kotlin.fqn.class]Unit #>*/ = takeFun<T> { it/*<# : |[inlayHint2.T:kotlin.fqn.class]T & Any #>*/ -> println(it)}