// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
class MyCollection<T> {
    companion object {
        fun of(elements: Int) = 3
    }
}

fun testCollection() = MyCollection<caret>.of(1)