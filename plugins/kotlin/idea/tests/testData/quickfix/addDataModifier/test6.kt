// "Make 'Foo' data class" "true"
// K2_ERROR: COMPONENT_FUNCTION_MISSING
// K2_ERROR: COMPONENT_FUNCTION_MISSING
class Foo(private val bar: String, protected var baz: Int) {
    class A {
        fun test() {
            var (bar, baz) = Foo("A", 1)<caret>
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddDataModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddDataModifierFixFactory$AddDataModifierFix