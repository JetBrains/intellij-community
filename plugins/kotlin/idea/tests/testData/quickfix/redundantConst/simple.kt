// "Remove 'const' modifier" "true"
class Foo {
    <caret>const val s = "s"
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase