// "class org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix" "false"
// ERROR: Cannot access 'foo': it is invisible (private in a supertype) in 'A'
// K2_AFTER_ERROR: Cannot access 'val foo: String': it is private in 'Base'.

open class Base(private val foo: String)

class A(foo: String) : Base(foo) {
    fun bar() {
        val a = foo<caret>
    }
}