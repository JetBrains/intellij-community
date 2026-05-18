// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

fun testCollection(x: MyCollection<Number>) { }
fun testCollection(x: List<Number>) { }

fun main() {
    testCollection(MyCollec<caret>tion.of(1, 2, 3))
}