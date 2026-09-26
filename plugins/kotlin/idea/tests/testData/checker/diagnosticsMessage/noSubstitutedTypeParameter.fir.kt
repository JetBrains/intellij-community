val f = listOf("").firstOrNull(<error descr="[ARGUMENT_TYPE_MISMATCH]">1</error>)

fun <T> listOf(element: T): List<T> = java.util.Collections.singletonList(element)
fun <T> Iterable<T>.firstOrNull(<warning>predicate</warning>: (T) -> Boolean): T? = null