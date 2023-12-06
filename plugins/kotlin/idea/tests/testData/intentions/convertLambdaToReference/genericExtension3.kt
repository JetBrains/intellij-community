// IS_APPLICABLE: false
// WITH_STDLIB
class Foo {
    inline fun <reified T: Any> bar(): String? = T::class.simpleName
}

fun test(list: List<Foo>) {
    list.forEach <caret>{ it.bar<Int>() }
}