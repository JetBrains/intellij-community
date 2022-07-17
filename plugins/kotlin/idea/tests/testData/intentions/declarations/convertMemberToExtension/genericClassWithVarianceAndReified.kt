// WITH_STDLIB
class Foo<out R> {
    val list = listOf<R>()
    inline fun <reified T> <caret>bar(x: T): Triple<List<R>, T, String> {
        return Triple(list, x, T::class.java.toString())
    }
}