// "Remove 'const' modifier" "true"
// K2_ERROR: TYPE_CANT_BE_USED_FOR_CONST_VAL

class Foo

object Test {
    <caret>const val c = Foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase