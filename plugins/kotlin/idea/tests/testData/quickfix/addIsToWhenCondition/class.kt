// "Add 'is' before 'Foo'" "true"

class Foo

fun test(a: Any) {
    when (a) {
        <caret>Foo -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIsToWhenConditionFix