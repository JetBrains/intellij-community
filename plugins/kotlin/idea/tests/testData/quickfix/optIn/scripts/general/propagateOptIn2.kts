// "Propagate 'UnstableApi' opt-in requirement to containing class 'Derived'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME
@file:OptIn(ExperimentalSubclassOptIn::class)

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
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$HighPriorityPropagateOptInAnnotationFix