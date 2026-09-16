// "Make 'bar' not open" "true"
object Foo {
    <caret>open fun bar() {}
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase