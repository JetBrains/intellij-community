// "Make constructor parameter a property" "true"

class A(foo: String) {
    fun bar() {
        foo<caret> = ""
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix