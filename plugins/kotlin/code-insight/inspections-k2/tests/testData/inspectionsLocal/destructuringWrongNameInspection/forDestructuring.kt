// WITH_STDLIB
data class Foo(val a: String, val b: Int)

fun bar(f: Foo) {
    for ((r, a<caret>) in listOf(Foo("1", 2)) {

    }
}