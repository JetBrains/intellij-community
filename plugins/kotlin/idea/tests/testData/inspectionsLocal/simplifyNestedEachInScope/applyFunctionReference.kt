// WITH_STDLIB
fun foo(num: Int) = println(num + 1)

fun test(): List<Int> {
    return listOf(1, 2, 3).<caret>apply { forEach(::foo) }.filter { it > 1 }
}