// "Replace with 'Factory<Int>()'" "true"
// K2_ACTION: "Replace with 'Factory()'" "true"
// WITH_STDLIB

class Foo<T> @Deprecated("", ReplaceWith("Factory()")) constructor()
fun <T> Factory(): Foo<T> = TODO()

fun baz() {
    val foo = <caret>Foo<Int>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2