// "Propagate 'TopMarker' opt-in requirement to 'topUserVal'" "true"
// IGNORE_FIR
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class TopMarker

@TopMarker
class TopClass

@Target(AnnotationTarget.TYPE)
@TopMarker
annotation class TopAnn

val topUserVal: @<caret>TopAnn TopClass? = null

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.OptInFixesFactory$PropagateOptInAnnotationFix