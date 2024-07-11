// "Replace with 'Factory<T>()'" "true"
// WITH_STDLIB

class Foo<T> @Deprecated("", ReplaceWith("Factory<T>()")) constructor()
fun <T> Factory(): Foo<T> = TODO()

fun baz() {
    val foo: Foo<Int> = <caret>Foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix