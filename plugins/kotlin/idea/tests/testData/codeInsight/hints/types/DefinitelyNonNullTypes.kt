// MODE: all
fun <T> inlayHint(x: T & Any)/*<# [:  []T & Any] #>*/ = x

fun <T>takeFun(f: (T & Any) -> Unit) {}
fun <T> inlayHint2()/*<# [:  [jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/Unit.kt:*]Unit] #>*/ = takeFun<T> { it/*<# [:  []T & Any] #>*/ -> println(it)}
