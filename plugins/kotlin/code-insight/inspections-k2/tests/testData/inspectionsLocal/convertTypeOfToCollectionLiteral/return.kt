// COMPILER_ARGUMENTS: -Xcollection-literals

class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

fun testCollection(): MyCollection<Number> {
    return MyColle<caret>ction.of(1, 2, 3)
}