// MODE: local_variable

fun foo(o: Any) {
    if (o !is String) return

    val s/*<# : |[kotlin.Any:kotlin.fqn.class]Any #>*/ = o
}