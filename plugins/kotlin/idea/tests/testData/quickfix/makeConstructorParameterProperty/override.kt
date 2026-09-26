// "class org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix" "false"
// ERROR: Cannot access 'foo': it is invisible (private in a supertype) in 'A'
// K2_AFTER_ERROR: INVISIBLE_REFERENCE
// K2_ERROR: INVISIBLE_REFERENCE

open class Base(private val foo: String)

class A(foo: String) : Base(foo) {
    fun bar() {
        val a = foo<caret>
    }
}