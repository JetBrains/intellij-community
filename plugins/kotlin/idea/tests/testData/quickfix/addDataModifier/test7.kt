// "Make 'Foo' data class" "true"
// K2_ERROR: Destructuring of type 'Foo' requires operator function 'component1()'.
// K2_ERROR: Destructuring of type 'Foo' requires operator function 'component2()'.
class Foo(internal val bar: String, var baz: Int)

fun test() {
    var (bar, baz) = Foo("A", 1)<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddDataModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddDataModifierFixFactory$AddDataModifierFix