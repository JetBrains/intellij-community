
val f = listOf("").firstOrNull(<error descr="[CONSTANT_EXPECTED_TYPE_MISMATCH] The integer literal does not conform to the expected type (TypeVariable(T)) -> Boolean">1</error>)

fun <T> listOf(element: T): List<T> = java.util.Collections.singletonList(element)
fun <T> Iterable<T>.firstOrNull(<warning>predicate</warning>: (T) -> Boolean): T? = null