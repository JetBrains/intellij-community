// PROBLEM: none
// LANGUAGE_VERSION: 1.7
class Foo<K>

fun <K> buildFoo(builderAction: Foo<K>.() -> Unit): Foo<K> = Foo()

fun <K> Foo<K>.bar(x: Int = 1) {}

fun main() {
    val x = buildFoo<Any?><caret> {
        bar()
    }
}
