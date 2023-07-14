// "Make 'Foo' data class" "true"
class Foo(val bar: String, var baz: Int)

fun test() {
    var (bar, baz) = Foo("A", 1)<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddDataModifierFix