// "Make constructor parameter a property" "true"

class A(foo: String) {
    fun bar() {
        val a = foo<caret>
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix