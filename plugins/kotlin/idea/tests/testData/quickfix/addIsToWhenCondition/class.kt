// "Add 'is' before 'Foo'" "true"
// K2_ERROR: Classifier 'class Foo : Any' does not have a companion object, so it cannot be used as an expression.

class Foo

fun test(a: Any) {
    when (a) {
        <caret>Foo -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIsToWhenConditionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIsToWhenConditionFix