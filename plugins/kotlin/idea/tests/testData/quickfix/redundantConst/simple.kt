// "Remove 'const' modifier" "true"
// K2_ERROR: Const 'val' is only allowed on top level, in named objects, in companion objects or companion blocks.
class Foo {
    <caret>const val s = "s"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase