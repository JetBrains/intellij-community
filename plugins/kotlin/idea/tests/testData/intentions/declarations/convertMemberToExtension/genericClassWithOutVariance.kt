// WITH_RUNTIME
class Foo<out R> {
    val list = listOf<R>()
    fun <caret>bar() = list.first() to list.last()
}