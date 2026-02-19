// "Suppress 'OVERRIDE_DEPRECATION' for fun foo" "true"
// WITH_STDLIB

open class Base {
    @Deprecated("Don't use")
    open fun foo() {}
}

class Derived : Base() {
    override fun <caret>foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction