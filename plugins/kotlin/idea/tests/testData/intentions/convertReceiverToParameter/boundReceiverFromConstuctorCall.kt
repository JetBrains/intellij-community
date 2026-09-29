// SHOULD_FAIL_WITH: Callable reference 'Foo&lt;Int&gt;()::fn' cannot be updated: its receiver value cannot be turned into an argument
class Foo<G>

fun <caret>Foo<Int>.fn(p: String) {}

fun usages() {
    val v = Foo<Int>()::fn
}