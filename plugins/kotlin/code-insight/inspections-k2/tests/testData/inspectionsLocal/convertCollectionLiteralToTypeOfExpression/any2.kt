// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none

class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

fun testCollection(): Any = [1.2<caret>]