// "Propagate 'MyExperimentalAPI' opt-in requirement to 'outer'" "true"
// PRIORITY: HIGH

@RequiresOptIn
@Target(AnnotationTarget.FUNCTION)
annotation class MyExperimentalAPI

open class Base {
    @MyExperimentalAPI
    open fun foo() {}
}

class Outer {
    fun outer() {
        class Derived : Base() {
            override fun foo<caret>() {}
        }
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix