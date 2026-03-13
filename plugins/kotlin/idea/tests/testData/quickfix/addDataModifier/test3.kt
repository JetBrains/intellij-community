// "Make 'Foo' data class" "true"
// WITH_STDLIB
// K2_ERROR: Destructuring of type 'Foo' requires operator function 'component1()'.
// K2_ERROR: Destructuring of type 'Foo' requires operator function 'component2()'.
class Foo(val bar: String, var baz: Int)

fun test() {
    val list = listOf(Foo("A", 1))
    list.forEach { (foo<caret>, bar) ->
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddDataModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddDataModifierFixFactory$AddDataModifierFix