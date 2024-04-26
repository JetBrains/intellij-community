// "Remove 'const' modifier" "true"

class Foo

object Test {
    <caret>const val c = Foo()
}
/* IGNORE_K2 */
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase