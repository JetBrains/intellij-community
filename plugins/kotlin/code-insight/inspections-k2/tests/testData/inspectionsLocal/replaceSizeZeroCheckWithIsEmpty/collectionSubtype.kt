// WITH_STDLIB

class Foo : AbstractCollection<Int>() {
    override val size = 0
    override fun iterator() = emptyList<Int>().iterator()
}

fun test(items: Foo) {
    items.size<caret> < 1
}