// "Make constructor parameter a property in class 'B'" "true"

class B(bar: String) {

    inner class A {
        fun foo() {
            val a = bar<caret>
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix