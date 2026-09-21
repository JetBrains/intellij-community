// MODE: all
fun <T> inlayHint(x: T & Any)/*<# : |[DefinitelyNonNullTypes.kt:18]T| & |Any #>*/ = x

fun <T>takeFun(f: (T & Any) -> Unit) {}
fun <T> inlayHint2()/*<# : |[kotlin.Unit:kotlin.fqn.class]Unit #>*/ = takeFun<T> { it/*<# : |[DefinitelyNonNullTypes.kt:93]T| & |Any #>*/ -> println(it)}