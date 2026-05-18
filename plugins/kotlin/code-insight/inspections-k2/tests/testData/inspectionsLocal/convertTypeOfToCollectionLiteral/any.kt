// COMPILER_ARGUMENTS: -Xcollection-literals

class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

fun testCollection() {
    val x: Any = MyCollection<caret>.of(1.2)
}