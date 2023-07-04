// "Propagate 'TopMarker' opt-in requirement to 'topUserVal'" "true"
// RUNTIME_WITH_SCRIPT_RUNTIME

@RequiresOptIn
annotation class TopMarker

@TopMarker
class TopClass

@Target(AnnotationTarget.TYPE)
@TopMarker
annotation class TopAnn

val topUserVal: @TopAnn <caret>TopClass? = null
