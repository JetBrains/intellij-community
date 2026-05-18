// COMPILER_ARGUMENTS: -Xcollection-literals

class MyCustomList<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCustomList<T> = TODO() }
}

fun testCustomList() {
    val x: MyCustomList<Int> = [1<caret>, 2, 3]
}