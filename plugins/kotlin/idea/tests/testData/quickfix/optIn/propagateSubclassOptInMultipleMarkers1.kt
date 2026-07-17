// "Propagate 'SubclassOptInRequired(UnstableApiA::class)' opt-in requirement to 'SomeImplementation'" "true"
// ERROR: This class or interface requires opt-in to be implemented. Its usage must be marked with '@UnstableApiB', '@OptIn(UnstableApiB::class)' or '@SubclassOptInRequired(UnstableApiB::class)'
// WITH_STDLIB
// K2_ERROR: OPT_IN_TO_INHERITANCE_ERROR
// K2_ERROR: OPT_IN_TO_INHERITANCE_ERROR
// K2_AFTER_ERROR: OPT_IN_TO_INHERITANCE_ERROR
@file:OptIn(ExperimentalSubclassOptIn::class)

@RequiresOptIn
annotation class UnstableApiA

@RequiresOptIn
annotation class UnstableApiB

@SubclassOptInRequired(UnstableApiA::class, UnstableApiB::class)
interface CoreLibraryApi

interface SomeImplementation : CoreLibraryApi<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixes$PropagateOptInAnnotationFix