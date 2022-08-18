// "Propagate 'TopMarker' opt-in requirement to 'topUserVal'" "true"
// COMPILER_ARGUMENTS: -opt-in=kotlin.RequiresOptIn
// WITH_STDLIB

@RequiresOptIn
annotation class TopMarker

@TopMarker
class TopClass

@Target(AnnotationTarget.TYPE)
@TopMarker
annotation class TopAnn

val topUserVal: @TopAnn <caret>TopClass? = null
