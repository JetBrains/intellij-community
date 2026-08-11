// "Propagate 'UnstableApi' opt-in requirement to containing class 'Derived'" "true"
// PRIORITY: HIGH
// RUNTIME_WITH_SCRIPT_RUNTIME
// LANGUAGE_VERSION: 2.1
// K2_ERROR: OPT_IN_OVERRIDE_ERROR
// K2_ERROR: OPT_IN_TO_INHERITANCE_ERROR

@RequiresOptIn
annotation class UnstableApi

@SubclassOptInRequired(UnstableApi::class)
interface Base {
    @UnstableApi
    fun foo()
}

abstract class Derived : Base {
    override fun foo<caret>(){}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix