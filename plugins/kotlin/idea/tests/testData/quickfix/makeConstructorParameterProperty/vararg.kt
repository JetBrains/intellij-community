// "Make constructor parameter a property" "true"

class SomeClass(vararg dismissibleViewTypes: Int) {
    fun someFun() {
        <caret>dismissibleViewTypes
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix