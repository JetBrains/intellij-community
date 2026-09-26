// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = MyCollection() }
}

fun testCollection() : Any = MyCollection<caret>.of(1.2)
