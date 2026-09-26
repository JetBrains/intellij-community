// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
class MyCollection<T> {
    companion object { operator fun <T> of(vararg elements: T): MyCollection<T> = TODO() }
}

fun <T> testCollection(x: T, y: T) { }

fun main() {
    testCollection([2, 3], ((MyC<caret>ollection.of())))
}