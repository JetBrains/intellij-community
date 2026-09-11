// "Make constructor parameter a property" "true"
// K2_ERROR: UNRESOLVED_REFERENCE

class SomeClass(vararg dismissibleViewTypes: Int) {
    fun someFun() {
        <caret>dismissibleViewTypes
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix